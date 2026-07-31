package com.vultisig.wallet.data.api.chains

import com.vultisig.wallet.data.api.models.EvmRpcResponseJson
import com.vultisig.wallet.data.api.models.RpcError
import com.vultisig.wallet.data.api.models.SuiTransactionBlockOptions
import com.vultisig.wallet.data.api.models.SuiTransactionBlockResponse
import com.vultisig.wallet.data.api.utils.RpcResponseJson
import com.vultisig.wallet.data.api.utils.postRpc
import io.ktor.client.HttpClient
import java.math.BigInteger
import javax.inject.Inject
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonArray
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import timber.log.Timber
import vultisig.keysign.v1.SuiCoin

interface SuiApi {

    suspend fun getBalance(address: String, contractAddress: String): BigInteger

    suspend fun getReferenceGasPrice(): BigInteger

    suspend fun getAllCoins(address: String): List<SuiCoin>

    /** The on-chain [SuiCoinMetadata] for [coinType], or `null` when the coin publishes none. */
    suspend fun getCoinMetadata(coinType: String): SuiCoinMetadata?

    suspend fun executeTransactionBlock(unsignedTransaction: String, signature: String): String

    suspend fun dryRunTransaction(transactionBytes: String): SuiDryRunResponse

    suspend fun checkStatus(txHash: String): SuiTransactionBlockResponse?

    suspend fun getLatestCheckpointSequenceNumber(): Long?
}

internal class SuiApiImpl
@Inject
constructor(private val http: HttpClient, private val json: Json) : SuiApi {

    private val rpcUrl = "https://sui-rpc.publicnode.com"

    override suspend fun getBalance(address: String, contractAddress: String): BigInteger {
        val response =
            http.postRpc<RpcResponseJson>(
                url = rpcUrl,
                method = "suix_getBalance",
                params =
                    buildJsonArray {
                        add(address)
                        if (contractAddress.isNotEmpty()) add(contractAddress)
                    },
            )

        return response.result?.jsonObject?.get("totalBalance")?.jsonPrimitive?.content?.let {
            BigInteger(it)
        } ?: error(response.error.describe("Failed to get sui balance"))
    }

    override suspend fun getReferenceGasPrice(): BigInteger {
        val response =
            http.postRpc<RpcResponseJson>(
                url = rpcUrl,
                method = "suix_getReferenceGasPrice",
                params = JsonArray(emptyList()),
            )
        return response.result?.jsonPrimitive?.content?.let { BigInteger(it) }
            ?: error(response.error.describe("Failed to fetch sui reference gas price"))
    }

    override suspend fun getAllCoins(address: String): List<SuiCoin> {
        val allCoins = mutableListOf<SuiCoin>()
        var cursor: String? = null

        // suix_getAllCoins is paginated (default page ~50 objects). Follow nextCursor/hasNextPage
        // so a wallet whose objects span multiple pages doesn't return a truncated set — otherwise
        // a token whose objects all land on a later page is invisible here and a token send gets
        // silently misclassified as a native SUI transfer. Mirrors iOS SuiService.getAllCoins.
        do {
            val response =
                http.postRpc<RpcResponseJson>(
                    url = rpcUrl,
                    method = "suix_getAllCoins",
                    params =
                        buildJsonArray {
                            add(address)
                            cursor?.let { add(it) }
                        },
                )
            val result =
                response.result?.jsonObject
                    ?: error(response.error.describe("Failed to fetch all coins for sui"))

            result["data"]?.jsonArray?.forEach { element ->
                val obj = element.jsonObject
                val coinType = obj["coinType"]?.jsonPrimitive?.content ?: return@forEach
                allCoins.add(
                    SuiCoin(
                        coinObjectId = obj["coinObjectId"]?.jsonPrimitive?.content ?: "",
                        version = obj["version"]?.jsonPrimitive?.content ?: "",
                        digest = obj["digest"]?.jsonPrimitive?.content ?: "",
                        balance = obj["balance"]?.jsonPrimitive?.content ?: "",
                        previousTransaction =
                            obj["previousTransaction"]?.jsonPrimitive?.content ?: "",
                        coinType = coinType,
                    )
                )
            }

            val hasNextPage = result["hasNextPage"]?.jsonPrimitive?.booleanOrNull ?: false
            cursor = if (hasNextPage) result["nextCursor"]?.jsonPrimitive?.content else null
        } while (cursor != null)

        return allCoins
    }

    override suspend fun getCoinMetadata(coinType: String): SuiCoinMetadata? {
        val response =
            http.postRpc<EvmRpcResponseJson<SuiCoinMetadata>>(
                url = rpcUrl,
                method = "suix_getCoinMetadata",
                params = buildJsonArray { add(coinType) },
            )
        // A null result is the node's answer for a coin that publishes no metadata object, and is
        // distinct from an RPC failure — only the latter may abort, so a transient outage is never
        // read as "this coin has no metadata".
        val error = response.error
        if (error != null) error(error.describe("Failed to fetch sui coin metadata"))
        return response.result
    }

    override suspend fun executeTransactionBlock(
        unsignedTransaction: String,
        signature: String,
    ): String {
        val response =
            http.postRpc<RpcResponseJson>(
                url = rpcUrl,
                method = "sui_executeTransactionBlock",
                params =
                    buildJsonArray {
                        add(unsignedTransaction)
                        addJsonArray { add(signature) }
                    },
            )
        return response.result?.jsonObject?.get("digest")?.jsonPrimitive?.content
            ?: error(response.error.describe("Failed to execute transaction block"))
    }

    override suspend fun dryRunTransaction(transactionBytes: String): SuiDryRunResponse {
        val response =
            http.postRpc<RpcResponseJson>(
                url = rpcUrl,
                method = "sui_dryRunTransactionBlock",
                params = buildJsonArray { add(JsonPrimitive(transactionBytes)) },
            )

        val dryRunResponse =
            response.result?.let { json.decodeFromJsonElement<SuiDryRunResponse>(it) }
                ?: error(response.error.describe("Failed to dry run transaction"))

        if (dryRunResponse.effects.status.error.isNotEmpty()) {
            throw Exception("Simulation Error: ${dryRunResponse.effects.status.error}")
        }

        return dryRunResponse
    }

    override suspend fun checkStatus(txHash: String): SuiTransactionBlockResponse? {
        val response =
            http.postRpc<EvmRpcResponseJson<SuiTransactionBlockResponse>>(
                url = rpcUrl,
                method = "sui_getTransactionBlock",
                params =
                    buildJsonArray {
                        add(txHash)
                        add(json.encodeToJsonElement(SuiTransactionBlockOptions()))
                    },
            )
        val error = response.error
        if (error != null) {
            // The node returns Invalid Params (-32602) when the digest hasn't landed yet — a
            // genuine not-found, safe to keep polling. Any other code is a terminal RPC failure
            // (rate limit, malformed digest, indexer outage) and must surface immediately instead
            // of being masked as the same retryable outcome.
            if (error.code == SUI_TX_NOT_FOUND_RPC_CODE) {
                Timber.d("checkStatus not found: %s", error.message)
                return null
            }
            throw SuiRpcException(error)
        }
        return response.result
    }

    override suspend fun getLatestCheckpointSequenceNumber(): Long? {
        val response =
            http.postRpc<EvmRpcResponseJson<String>>(
                url = rpcUrl,
                method = "sui_getLatestCheckpointSequenceNumber",
                params = JsonArray(emptyList()),
            )
        if (response.result == null) {
            Timber.d("getLatestCheckpointSequenceNumber error: ${response.error?.message}")
            return null
        }
        return response.result.toLongOrNull()
    }

    private companion object {
        // Verified against iOS's SuiTransactionStatusProvider: Sui's full node returns this
        // JSON-RPC "Invalid params" code, not a dedicated one, when a digest hasn't landed yet.
        const val SUI_TX_NOT_FOUND_RPC_CODE = -32602
    }
}

/** Appends the JSON-RPC error's message and code to [fallback] when present. */
private fun RpcError?.describe(fallback: String): String =
    if (this == null) fallback else "$fallback: $message (code $code)"

/**
 * A terminal Sui JSON-RPC error surfaced by [SuiApi.checkStatus] — any [RpcError] other than the
 * not-found code. Lets [SuiStatusProvider][com.vultisig.wallet.data.api.txstatus.SuiStatusProvider]
 * report a persistent RPC failure immediately instead of masking it as a retryable pending poll.
 */
internal class SuiRpcException(val rpcError: RpcError) :
    Exception("Sui RPC error ${rpcError.code}: ${rpcError.message}")

/**
 * A Sui coin's on-chain `CoinMetadata` object. [decimals] and [symbol] are required: a coin the
 * node cannot describe must be dropped rather than shown at a guessed magnitude or under a
 * placeholder ticker.
 */
@Serializable
data class SuiCoinMetadata(
    @SerialName("decimals") val decimals: Int,
    @SerialName("symbol") val symbol: String,
    @SerialName("iconUrl") val iconUrl: String? = null,
)

@Serializable
data class SuiDryRunResponse(@SerialName("effects") val effects: SuiTransactionEffects)

@Serializable
data class SuiTransactionEffects(
    @SerialName("status") val status: SuiEffectStatus,
    @SerialName("gasUsed") val gasUsed: SuiEffectGasUsed,
)

@Serializable
data class SuiEffectStatus(
    @SerialName("status") val status: String,
    @SerialName("error") val error: String = "",
)

@Serializable
data class SuiEffectGasUsed(
    @SerialName("computationCost") val computationCost: String,
    @SerialName("storageCost") val storageCost: String,
    @SerialName("storageRebate") val storageRebate: String = "0",
)

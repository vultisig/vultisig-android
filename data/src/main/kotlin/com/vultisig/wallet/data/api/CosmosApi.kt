package com.vultisig.wallet.data.api

import com.vultisig.wallet.data.api.errors.CosmosBroadcastException
import com.vultisig.wallet.data.api.errors.parseCosmosBroadcastResponse
import com.vultisig.wallet.data.api.models.DenomMetadata
import com.vultisig.wallet.data.api.models.MetadataResponse
import com.vultisig.wallet.data.api.models.cosmos.CosmosBalance
import com.vultisig.wallet.data.api.models.cosmos.CosmosBalanceResponse
import com.vultisig.wallet.data.api.models.cosmos.CosmosIbcDenomTraceDenomTraceJson
import com.vultisig.wallet.data.api.models.cosmos.CosmosIbcDenomTraceJson
import com.vultisig.wallet.data.api.models.cosmos.CosmosTHORChainAccountResponse
import com.vultisig.wallet.data.api.models.cosmos.CosmosTxStatusJson
import com.vultisig.wallet.data.api.models.cosmos.THORChainAccountValue
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.repositories.CustomRpcRepository
import com.vultisig.wallet.data.utils.CosmosThorChainResponseSerializer
import com.vultisig.wallet.data.utils.NetworkException
import com.vultisig.wallet.data.utils.bodyOrThrow
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.util.encodeBase64
import java.net.URLEncoder
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import timber.log.Timber

interface CosmosApi {
    suspend fun getBalance(address: String): List<CosmosBalance>

    suspend fun getAccountNumber(address: String): THORChainAccountValue

    suspend fun broadcastTransaction(tx: String): String?

    suspend fun getWasmTokenBalance(address: String, contractAddress: String): CosmosBalance

    suspend fun getIbcDenomTraces(contractAddress: String): CosmosIbcDenomTraceDenomTraceJson

    /**
     * Chain-declared metadata for the given bank denom, or `null` when the chain has no entry or
     * the request fails. Used by token auto-discovery to resolve display ticker and decimals.
     */
    suspend fun getDenomMetadata(denom: String): DenomMetadata?

    suspend fun getLatestBlock(): String

    suspend fun getTxStatus(txHash: String): CosmosTxStatusJson?
}

interface CosmosApiFactory {
    fun createCosmosApi(chain: Chain): CosmosApi
}

internal class CosmosApiFactoryImp
@Inject
constructor(
    private val httpClient: HttpClient,
    private val json: Json,
    private val cosmosThorChainResponseSerializer: CosmosThorChainResponseSerializer,
    private val customRpcRepository: CustomRpcRepository,
) : CosmosApiFactory {
    override fun createCosmosApi(chain: Chain): CosmosApi {
        val defaultApiUrl =
            when (chain) {
                Chain.GaiaChain -> "https://cosmos-rest.publicnode.com"
                Chain.Kujira -> "https://kujira-rest.publicnode.com"
                Chain.Dydx -> "https://dydx-rest.publicnode.com"
                Chain.Osmosis -> "https://osmosis-rest.publicnode.com"
                Chain.Terra -> "https://terra-lcd.publicnode.com"
                Chain.TerraClassic -> "https://terra-classic-lcd.publicnode.com"
                Chain.Noble -> "https://noble-api.polkachu.com"
                Chain.Akash -> "https://akash-rest.publicnode.com"
                Chain.Qbtc -> "https://api.vultisig.com/qbtc-rpc"
                else -> throw IllegalArgumentException("Unsupported chain $chain")
            }

        // App-wide custom RPC override (#4787): Qbtc is excluded from the supported set, so it
        // always
        // resolves to its default proxy here. Unset chains keep their default endpoint.
        val apiUrl = customRpcRepository.urlFor(chain) ?: defaultApiUrl

        return CosmosApiImp(httpClient, apiUrl, json, cosmosThorChainResponseSerializer)
    }
}

internal class CosmosApiImp(
    private val httpClient: HttpClient,
    private val rpcEndpoint: String,
    private val json: Json,
    private val cosmosThorChainResponseSerializer: CosmosThorChainResponseSerializer,
) : CosmosApi {
    override suspend fun getBalance(address: String): List<CosmosBalance> {
        val response = httpClient.get("$rpcEndpoint/cosmos/bank/v1beta1/balances/$address")
        val resp = response.bodyOrThrow<CosmosBalanceResponse>()
        return resp.balances ?: emptyList()
    }

    override suspend fun getAccountNumber(address: String): THORChainAccountValue {
        val response = httpClient.get("$rpcEndpoint/cosmos/auth/v1beta1/accounts/$address") {}
        val responseBody = response.bodyAsText()
        val decodedResponse = json.decodeFromString(cosmosThorChainResponseSerializer, responseBody)

        Timber.d("getAccountNumber: $responseBody")
        return when (decodedResponse) {
            is CosmosTHORChainAccountResponse.Error ->
                THORChainAccountValue(accountNumber = "0", sequence = "0", address = null)

            is CosmosTHORChainAccountResponse.Success ->
                decodedResponse.response.account ?: error("Error getting account")
        }
    }

    override suspend fun broadcastTransaction(tx: String): String? {
        try {
            val response =
                httpClient.post("$rpcEndpoint/cosmos/tx/v1beta1/txs") {
                    contentType(ContentType.Application.Json)
                    setBody(tx)
                }
            return parseCosmosBroadcastResponse(
                rawBody = response.bodyAsText(),
                logTag = "CosmosApiService",
                json = json,
            )
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            if (e !is CosmosBroadcastException) {
                Timber.tag("CosmosApiService").e(e, "Error broadcasting transaction")
            }
            throw e
        }
    }

    override suspend fun getWasmTokenBalance(
        address: String,
        contractAddress: String,
    ): CosmosBalance {
        val payload = "{\"balance\":{\"address\":\"$address\"}}".encodeBase64()

        return CosmosBalance(
            denom = contractAddress,
            amount =
                httpClient
                    .get("$rpcEndpoint/cosmwasm/wasm/v1/contract/$contractAddress/smart/$payload")
                    .bodyOrThrow<JsonObject>()["data"]
                    ?.jsonObject
                    ?.get("balance")
                    ?.jsonPrimitive
                    ?.content ?: "0",
        )
    }

    override suspend fun getIbcDenomTraces(
        contractAddress: String
    ): CosmosIbcDenomTraceDenomTraceJson {
        val hash = contractAddress.removePrefix("ibc/")
        return httpClient
            .get("$rpcEndpoint/ibc/apps/transfer/v1/denom_traces/$hash")
            .bodyOrThrow<CosmosIbcDenomTraceJson>()
            .denomTrace!!
    }

    override suspend fun getDenomMetadata(denom: String): DenomMetadata? {
        return try {
            val encoded = URLEncoder.encode(denom, Charsets.UTF_8.name())
            httpClient
                .get("$rpcEndpoint/cosmos/bank/v1beta1/denoms_metadata/$encoded")
                .bodyOrThrow<MetadataResponse>()
                .metadata
        } catch (e: CancellationException) {
            throw e
        } catch (e: NetworkException) {
            if (e.httpStatusCode == HttpStatusCode.NotFound.value) {
                Timber.d("No denom metadata for %s (expected for non-standard denoms)", denom)
            } else {
                Timber.e(e, "Failed to fetch denom metadata for %s", denom)
            }
            null
        } catch (e: Exception) {
            Timber.e(e, "Failed to fetch denom metadata for %s", denom)
            null
        }
    }

    override suspend fun getLatestBlock(): String {
        return httpClient
            .get("$rpcEndpoint/cosmos/base/tendermint/v1beta1/blocks/latest")
            .bodyOrThrow<JsonObject>()
            .jsonObject["block"]
            ?.jsonObject
            ?.get("header")
            ?.jsonObject
            ?.get("height")
            ?.jsonPrimitive
            ?.content ?: "0"
    }

    override suspend fun getTxStatus(txHash: String): CosmosTxStatusJson? {
        val response = httpClient.get("$rpcEndpoint/cosmos/tx/v1beta1/txs/$txHash")
        if (response.status.value == 404) return null
        return response.bodyOrThrow<CosmosTxStatusJson>()
    }
}

package com.vultisig.wallet.data.api.chains

import com.vultisig.wallet.data.api.models.SuiExecutionStatus
import com.vultisig.wallet.data.api.models.SuiTransactionBlockEffects
import com.vultisig.wallet.data.api.models.SuiTransactionBlockResponse
import io.ktor.client.HttpClient
import java.math.BigInteger
import javax.inject.Inject
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import timber.log.Timber
import vultisig.keysign.v1.SuiCoin

/**
 * Backstop on the coin-object walk in [SuiApi.getAllCoins]: at 50 objects per page this is 5000
 * coin objects, far beyond any real wallet, so reaching it means the connection is misbehaving
 * rather than that someone genuinely holds that many.
 */
internal const val SUI_MAX_COIN_PAGES = 100

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

/**
 * Sui reads, simulation and broadcast over GraphQL RPC.
 *
 * Sui is retiring JSON-RPC (full decommission mid-October 2026), so none of the `suix_*`/`sui_*`
 * methods this used to call will exist. See [SUI_GRAPHQL_ENDPOINTS] for why GraphQL was picked over
 * gRPC and how failover is configured.
 *
 * The public surface is unchanged: every method returns exactly what its JSON-RPC predecessor did,
 * so call sites, models and the coin-type conventions in
 * [SuiHelper][com.vultisig.wallet.data.crypto.SuiHelper] are untouched by the transport swap.
 */
internal class SuiApiImpl @Inject constructor(http: HttpClient, private val json: Json) : SuiApi {

    private val graphQl = SuiGraphQlTransport(http)

    override suspend fun getBalance(address: String, contractAddress: String): BigInteger {
        val response =
            graphQl
                .query(
                    BALANCE_QUERY,
                    buildJsonObject {
                        put("owner", address)
                        put("coinType", contractAddress.ifEmpty { NATIVE_SUI_COIN_TYPE })
                    },
                )
                .decode<BalanceResponse>()

        // An address that has never held the coin type resolves to null rather than an error —
        // that is a genuine zero, not an upstream fault. A malformed address is refused by the node
        // with a populated `errors` array, which the transport has already raised by this point.
        val totalBalance = response.address?.balance?.totalBalance ?: return BigInteger.ZERO

        // A present-but-unparsable amount is a node contract violation, not an empty wallet, so it
        // is raised rather than collapsed into the same zero an absent balance returns.
        return totalBalance.toBigIntegerOrNull()
            ?: throw SuiRpcException("unparsable balance: $totalBalance")
    }

    override suspend fun getReferenceGasPrice(): BigInteger =
        graphQl
            .query(REFERENCE_GAS_PRICE_QUERY)
            .decode<EpochResponse>()
            .epoch
            ?.referenceGasPrice
            ?.toBigIntegerOrNull() ?: throw SuiRpcException("failed to fetch reference gas price")

    override suspend fun getAllCoins(address: String): List<SuiCoin> {
        val allCoins = mutableListOf<SuiCoin>()
        var cursor: String? = null
        var pagesFetched = 0

        // The object connection is paginated. Follow hasNextPage/endCursor so a wallet whose
        // objects span multiple pages doesn't return a truncated set — otherwise a token whose
        // objects all land on a later page is invisible here and a token send gets silently
        // misclassified as a native SUI transfer.
        do {
            val objects =
                graphQl
                    .query(
                        ALL_COINS_QUERY,
                        buildJsonObject {
                            put("owner", address)
                            put("cursor", cursor)
                        },
                    )
                    .decode<AllCoinsResponse>()
                    .address
                    ?.objects ?: throw SuiRpcException("failed to fetch all coins for sui")

            objects.nodes.forEach { node ->
                val coinType = node.contents?.type?.repr?.let(::unwrapCoinType) ?: return@forEach
                allCoins.add(
                    SuiCoin(
                        coinObjectId = node.address.orEmpty(),
                        version = node.version?.toString().orEmpty(),
                        digest = node.digest.orEmpty(),
                        balance = node.contents.json?.balance.orEmpty(),
                        previousTransaction = node.previousTransaction?.digest.orEmpty(),
                        coinType = coinType,
                    )
                )
            }

            pagesFetched++

            // Termination cannot rest on the node alone: a `hasNextPage` that never flips, or an
            // `endCursor` that never advances, would spin here forever while `allCoins` grows
            // without bound. Stop on a repeated cursor, and cap the walk at [SUI_MAX_COIN_PAGES].
            val nextCursor = objects.pageInfo.endCursor.takeIf { objects.pageInfo.hasNextPage }
            if (
                nextCursor != null && (nextCursor == cursor || pagesFetched >= SUI_MAX_COIN_PAGES)
            ) {
                Timber.w(
                    "Sui coin pagination stopped after %d pages with more reported; cursor %s",
                    pagesFetched,
                    if (nextCursor == cursor) "did not advance" else "budget exhausted",
                )
                cursor = null
            } else {
                cursor = nextCursor
            }
        } while (cursor != null)

        return allCoins
    }

    override suspend fun getCoinMetadata(coinType: String): SuiCoinMetadata? {
        // A null result is the node's answer for a coin that publishes no metadata object, and is
        // distinct from a node failure — only the latter may abort (already raised by the
        // transport), so a transient outage is never read as "this coin has no metadata".
        val metadata =
            graphQl
                .query(COIN_METADATA_QUERY, buildJsonObject { put("coinType", coinType) })
                .decode<CoinMetadataResponse>()
                .coinMetadata ?: return null

        // decimals and symbol are required: a coin the node cannot describe must be dropped rather
        // than shown at a guessed magnitude or under a placeholder ticker.
        val decimals = metadata.decimals ?: return null
        val symbol = metadata.symbol ?: return null
        return SuiCoinMetadata(decimals = decimals, symbol = symbol, iconUrl = metadata.iconUrl)
    }

    override suspend fun executeTransactionBlock(
        unsignedTransaction: String,
        signature: String,
    ): String =
        graphQl
            .query(
                EXECUTE_TRANSACTION_MUTATION,
                buildJsonObject {
                    put("txBytes", unsignedTransaction)
                    put("signatures", buildJsonArray { add(signature) })
                },
            )
            .decode<ExecuteTransactionResponse>()
            .executeTransaction
            ?.effects
            ?.digest ?: throw SuiRpcException("failed to execute transaction block")

    override suspend fun dryRunTransaction(transactionBytes: String): SuiDryRunResponse {
        // `simulateTransaction` takes a JSON-encoded `sui.rpc.v2.Transaction`, whose `bcs` field
        // carries the same base64 BCS bytes the JSON-RPC dry run accepted directly. Gas selection
        // stays off because the caller already built a complete gas payment — letting the node
        // re-pick it would estimate a different transaction than the one that gets signed.
        val effects =
            graphQl
                .query(
                    SIMULATE_TRANSACTION_QUERY,
                    buildJsonObject {
                        putJsonObject("tx") {
                            putJsonObject("bcs") { put("value", transactionBytes) }
                        }
                    },
                )
                .decode<SimulateTransactionResponse>()
                .simulateTransaction
                ?.effects ?: throw SuiRpcException("failed to dry run transaction")

        val error = effects.errorMessage()
        if (error.isNotEmpty()) {
            error("Simulation Error: $error")
        }

        val gasSummary =
            effects.gasEffects?.gasSummary
                ?: throw SuiRpcException("dry run returned no gas summary")

        return SuiDryRunResponse(
            effects =
                SuiTransactionEffects(
                    status = SuiEffectStatus(status = effects.normalizedStatus(), error = error),
                    gasUsed =
                        SuiEffectGasUsed(
                            computationCost = gasSummary.computationCost.toString(),
                            storageCost = gasSummary.storageCost.toString(),
                            storageRebate = gasSummary.storageRebate.toString(),
                        ),
                )
        )
    }

    override suspend fun checkStatus(txHash: String): SuiTransactionBlockResponse? {
        // A node refusal arrives as a populated `errors` array and is raised by the transport as a
        // SuiRpcException, letting SuiStatusProvider report a persistent failure immediately. A
        // digest that simply hasn't landed yet resolves to a null transaction with no errors — a
        // genuine not-found, safe to keep polling.
        val transaction =
            graphQl
                .query(TRANSACTION_QUERY, buildJsonObject { put("digest", txHash) })
                .decode<TransactionResponse>()
                .transaction ?: return null

        val digest = transaction.digest ?: txHash

        return SuiTransactionBlockResponse(
            digest = digest,
            checkpoint = transaction.effects?.checkpoint?.sequenceNumber,
            effects =
                transaction.effects?.let { effects ->
                    SuiTransactionBlockEffects(
                        status =
                            SuiExecutionStatus(
                                status = effects.normalizedStatus(),
                                error = effects.errorMessage().ifEmpty { null },
                            ),
                        transactionDigest = digest,
                    )
                },
        )
    }

    override suspend fun getLatestCheckpointSequenceNumber(): Long? =
        try {
            graphQl
                .query(LATEST_CHECKPOINT_QUERY)
                .decode<CheckpointResponse>()
                .checkpoint
                ?.sequenceNumber
        } catch (e: SuiRpcException) {
            Timber.d("getLatestCheckpointSequenceNumber error: %s", e.errorMessage)
            null
        }

    /**
     * Decodes a GraphQL `data` object into [T].
     *
     * A shape the selection set cannot describe is a node contract violation, not an empty result,
     * so it is raised as a [SuiRpcException] rather than surfacing as a silent zero balance or an
     * empty coin list.
     */
    private inline fun <reified T> JsonObject.decode(): T =
        try {
            json.decodeFromJsonElement<T>(this)
        } catch (e: SerializationException) {
            // Carry the cause: the message alone does not say which field of which selection set
            // failed, and a decode failure against a live node is hard to place without the trace.
            throw SuiRpcException("unexpected response shape: ${e.message}", cause = e)
        }

    private companion object {
        const val NATIVE_SUI_COIN_TYPE = "0x2::sui::SUI"

        /** Page size for the coin-object connection, matching the old JSON-RPC default. */
        const val COIN_PAGE_SIZE = 50

        val BALANCE_QUERY =
            """
            query getBalance(${'$'}owner: SuiAddress!, ${'$'}coinType: String!) {
              address(address: ${'$'}owner) {
                balance(coinType: ${'$'}coinType) { totalBalance }
              }
            }
            """
                .trimIndent()

        val REFERENCE_GAS_PRICE_QUERY = "query { epoch { referenceGasPrice } }"

        val ALL_COINS_QUERY =
            """
            query getAllCoins(${'$'}owner: SuiAddress!, ${'$'}cursor: String) {
              address(address: ${'$'}owner) {
                objects(first: $COIN_PAGE_SIZE, after: ${'$'}cursor, filter: { type: "0x2::coin::Coin" }) {
                  pageInfo { hasNextPage endCursor }
                  nodes {
                    address
                    version
                    digest
                    previousTransaction { digest }
                    contents { type { repr } json }
                  }
                }
              }
            }
            """
                .trimIndent()

        val COIN_METADATA_QUERY =
            """
            query getCoinMetadata(${'$'}coinType: String!) {
              coinMetadata(coinType: ${'$'}coinType) { decimals symbol iconUrl }
            }
            """
                .trimIndent()

        val EXECUTE_TRANSACTION_MUTATION =
            """
            mutation executeTransaction(${'$'}txBytes: Base64!, ${'$'}signatures: [Base64!]!) {
              executeTransaction(transactionDataBcs: ${'$'}txBytes, signatures: ${'$'}signatures) {
                effects { digest }
              }
            }
            """
                .trimIndent()

        val SIMULATE_TRANSACTION_QUERY =
            """
            query simulateTransaction(${'$'}tx: JSON!) {
              simulateTransaction(transaction: ${'$'}tx, checksEnabled: true, doGasSelection: false) {
                effects {
                  status
                  executionError { message abortCode identifier }
                  gasEffects { gasSummary { computationCost storageCost storageRebate } }
                }
              }
            }
            """
                .trimIndent()

        val TRANSACTION_QUERY =
            """
            query getTransaction(${'$'}digest: String!) {
              transaction(digest: ${'$'}digest) {
                digest
                effects {
                  status
                  executionError { message abortCode identifier }
                  checkpoint { sequenceNumber }
                }
              }
            }
            """
                .trimIndent()

        val LATEST_CHECKPOINT_QUERY = "query { checkpoint { sequenceNumber } }"
    }
}

/**
 * The GraphQL selection sets above, as types.
 *
 * Every field is nullable with a null default: GraphQL resolves an absent or unreachable branch to
 * `null` rather than omitting it, and each query selects only part of a shared type (a simulation
 * asks for `gasEffects`, a status poll for `checkpoint`), so no single field is guaranteed present.
 *
 * Numeric fields follow the schema's scalar, not convenience: `BigInt` arrives as a JSON string
 * (and can exceed [Long]), `UInt53` as a JSON number. The injected [Json] is not lenient, so typing
 * one as the other fails to decode.
 */
@Serializable private data class BalanceResponse(val address: BalanceAddress? = null)

@Serializable private data class BalanceAddress(val balance: BalanceAmount? = null)

@Serializable private data class BalanceAmount(val totalBalance: String? = null)

@Serializable private data class EpochResponse(val epoch: EpochData? = null)

@Serializable private data class EpochData(val referenceGasPrice: String? = null)

@Serializable private data class AllCoinsResponse(val address: CoinObjectOwner? = null)

@Serializable private data class CoinObjectOwner(val objects: CoinObjectConnection? = null)

@Serializable
private data class CoinObjectConnection(
    val pageInfo: PageInfo = PageInfo(),
    val nodes: List<CoinObjectNode> = emptyList(),
)

@Serializable
private data class PageInfo(val hasNextPage: Boolean = false, val endCursor: String? = null)

@Serializable
private data class CoinObjectNode(
    val address: String? = null,
    val version: Long? = null,
    val digest: String? = null,
    val previousTransaction: DigestRef? = null,
    val contents: MoveObjectContents? = null,
)

@Serializable private data class DigestRef(val digest: String? = null)

@Serializable
private data class MoveObjectContents(val type: MoveTypeRef? = null, val json: CoinFields? = null)

@Serializable private data class MoveTypeRef(val repr: String? = null)

@Serializable private data class CoinFields(val balance: String? = null)

@Serializable private data class CoinMetadataResponse(val coinMetadata: CoinMetadataFields? = null)

@Serializable
private data class CoinMetadataFields(
    val decimals: Int? = null,
    val symbol: String? = null,
    val iconUrl: String? = null,
)

@Serializable
private data class ExecuteTransactionResponse(val executeTransaction: ExecutionResult? = null)

@Serializable private data class ExecutionResult(val effects: TransactionEffectsFields? = null)

@Serializable
private data class SimulateTransactionResponse(val simulateTransaction: SimulationResult? = null)

@Serializable private data class SimulationResult(val effects: TransactionEffectsFields? = null)

@Serializable private data class TransactionResponse(val transaction: TransactionFields? = null)

@Serializable
private data class TransactionFields(
    val digest: String? = null,
    val effects: TransactionEffectsFields? = null,
)

/** Shared across the execute, simulate and status selections; each asks for a subset. */
@Serializable
private data class TransactionEffectsFields(
    val digest: String? = null,
    val status: String? = null,
    val executionError: ExecutionError? = null,
    val checkpoint: CheckpointRef? = null,
    val gasEffects: GasEffects? = null,
)

@Serializable
private data class ExecutionError(
    val message: String? = null,
    val abortCode: String? = null,
    val identifier: String? = null,
)

@Serializable private data class CheckpointRef(val sequenceNumber: Long? = null)

@Serializable private data class GasEffects(val gasSummary: GasSummary? = null)

@Serializable
private data class GasSummary(
    val computationCost: Long = 0,
    val storageCost: Long = 0,
    val storageRebate: Long = 0,
)

@Serializable private data class CheckpointResponse(val checkpoint: CheckpointRef? = null)

/**
 * The execution status in the lowercase spelling the app's models expect.
 *
 * GraphQL reports the enum as `SUCCESS`/`FAILURE`, while
 * [SuiStatusProvider][com.vultisig.wallet.data.api.txstatus.SuiStatusProvider] and the effect
 * models match on the lowercase JSON-RPC spelling — so it is lowercased here rather than at each
 * comparison, where a missed site would silently report a confirmed transaction as pending.
 */
private fun TransactionEffectsFields.normalizedStatus(): String = status.orEmpty().lowercase()

/** The execution failure reason, or an empty string when the transaction did not abort. */
private fun TransactionEffectsFields.errorMessage(): String =
    executionError?.let {
        it.message ?: it.identifier ?: it.abortCode?.let { code -> "aborted with code $code" }
    } ?: ""

/**
 * The coin type a `0x2::coin::Coin<T>` object holds, in the same spelling JSON-RPC returned.
 *
 * Two adjustments, both required to keep the transport swap invisible to the rest of the app:
 * 1. The object connection reports the wrapper struct, whereas the app compares against the bare
 *    `T`. A wrapper string would match no known coin and turn every native send into a token send.
 * 2. GraphQL always spells the address zero-padded (`0x000…002::sui::SUI`) where JSON-RPC returned
 *    it stripped (`0x2::sui::SUI`). Comparisons already go through
 *    [SuiHelper.isSameSuiCoinType][com.vultisig.wallet.data.crypto.SuiHelper] and tolerate either,
 *    but [SuiTokenFinder][com.vultisig.wallet.data.usecases.SuiTokenFinder] *persists* this exact
 *    string as a discovered token's `contractAddress` — so stripping here keeps a token discovered
 *    after the migration identical to the same token discovered before it, rather than a second
 *    entry that only differs by padding.
 *
 * Verified against a live mainnet address: with this normalization the returned coin types match
 * `suix_getAllCoins` exactly. Only the address is normalized — Move module and struct identifiers
 * stay case-sensitive, matching [SuiHelper][com.vultisig.wallet.data.crypto.SuiHelper], because
 * `::coin::USDC` and `::coin::usdc` are genuinely distinct Move types.
 *
 * Returns null for anything that isn't a generic instantiation, which the type filter excludes.
 *
 * Internal rather than private so tests that build coins from a raw GraphQL payload normalize
 * through this exact function instead of a copy that can drift from it.
 */
internal fun unwrapCoinType(repr: String): String? {
    val open = repr.indexOf('<')
    val close = repr.lastIndexOf('>')
    if (open !in 0..<close) return null
    val coinType = repr.substring(open + 1, close)

    val addressEnd = coinType.indexOf("::")
    if (addressEnd < 0) return coinType
    val address = coinType.substring(0, addressEnd).lowercase().removePrefix("0x").trimStart('0')
    return "0x" + address.ifEmpty { "0" } + coinType.substring(addressEnd)
}

private fun String.toBigIntegerOrNull(): BigInteger? = runCatching { BigInteger(this) }.getOrNull()

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

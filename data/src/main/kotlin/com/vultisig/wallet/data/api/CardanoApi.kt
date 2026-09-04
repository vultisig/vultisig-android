package com.vultisig.wallet.data.api

import com.vultisig.wallet.data.api.models.cardano.CardanoAssetResponseJson
import com.vultisig.wallet.data.api.models.cardano.CardanoBalanceResponseJson
import com.vultisig.wallet.data.api.models.cardano.CardanoSlotResponseJson
import com.vultisig.wallet.data.api.models.cardano.CardanoTxStatusResponseJson
import com.vultisig.wallet.data.api.models.cardano.CardanoUtxoRequestJson
import com.vultisig.wallet.data.api.models.cardano.CardanoUtxoResponseJson
import com.vultisig.wallet.data.api.models.cardano.OgmiosError
import com.vultisig.wallet.data.api.models.cardano.OgmiosTransactionResponse
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.cardanoAssetId
import com.vultisig.wallet.data.models.parseCardanoAssetId
import com.vultisig.wallet.data.models.payload.CardanoTokenAsset
import com.vultisig.wallet.data.models.payload.UtxoInfo
import com.vultisig.wallet.data.utils.bodyOrThrow
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.http.path
import java.math.BigInteger
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import timber.log.Timber

/**
 * The Ogmios node rejected our broadcast because the inputs are already spent — its own message
 * ("Transaction has probably already been included") is the duplicate-broadcast signal on the
 * losing device of a multi-device keysign. Since the peer's byte-identical tx spent those exact
 * inputs, our locally computed hash is canonical (issue #5337).
 */
class CardanoTransactionAlreadyBroadcastException(message: String) : Exception(message)

interface CardanoApi {
    suspend fun getBalance(coin: Coin): BigInteger

    /** The held quantity of the native asset [coin] names through its `contractAddress`. */
    suspend fun getTokenBalance(coin: Coin): BigInteger

    suspend fun getUTXOs(coin: Coin): List<UtxoInfo>

    suspend fun getTxStatus(txHash: String): CardanoTxStatusResponseJson?

    suspend fun calculateDynamicTTL(): ULong

    suspend fun broadcastTransaction(chain: String, signedTransaction: String): String?
}

internal class CardanoApiImpl
@Inject
constructor(private val httpClient: HttpClient, private val json: Json) : CardanoApi {
    private val url: String = "https://api.koios.rest"
    private val apiV1Path: String = "api/v1"
    private val ogmiosUrl = "https://api.vultisig.com/ada/"

    private companion object {
        // Ogmios "UnknownOutputReference": the tx spends inputs the ledger no longer knows.
        const val OGMIOS_UNKNOWN_OUTPUT_REFERENCE_CODE = 3117
        // Koios truncates an unpaginated response at 1000 rows, so address_assets has to be
        // walked page by page: an address holding more distinct native assets than that would
        // otherwise drop the rows the curated token actually sits in and report a zero balance.
        const val KOIOS_PAGE_SIZE = 1000
        // Stops the walk if the node ever keeps returning full pages (50k distinct assets).
        const val KOIOS_MAX_PAGES = 50
    }

    override suspend fun getBalance(coin: Coin): BigInteger {

        val requestBody = mapOf("_addresses" to listOf(coin.address))
        val response =
            httpClient.post(url) {
                url { path(apiV1Path, "address_info") }
                setBody(requestBody)
            }
        return try {
            val balances = response.bodyOrThrow<List<CardanoBalanceResponseJson>>()
            val balanceString = balances.firstOrNull()?.balance ?: "0"
            BigInteger(balanceString)
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Timber.e("Error in Cardano getBalance : ${e.message}")
            throw e
        }
    }

    override suspend fun getTokenBalance(coin: Coin): BigInteger {
        val assetId = coin.contractAddress.lowercase()
        require(assetId.isNotBlank()) { "Cardano token ${coin.ticker} has no asset id" }

        val requestBody = mapOf("_addresses" to listOf(coin.address))
        return try {
            var total = BigInteger.ZERO
            var walkedToLastPage = false
            for (page in 0 until KOIOS_MAX_PAGES) {
                val assets =
                    httpClient
                        .post(url) {
                            url { path(apiV1Path, "address_assets") }
                            parameter("offset", page * KOIOS_PAGE_SIZE)
                            parameter("limit", KOIOS_PAGE_SIZE)
                            setBody(requestBody)
                        }
                        .bodyOrThrow<List<CardanoAssetResponseJson>>()

                // An address can hold the same asset across several UTXOs, so Koios may return
                // more than one row for it; the wallet balance is their sum.
                total =
                    assets
                        .filter { cardanoAssetId(it.policyId ?: "", it.assetName ?: "") == assetId }
                        .fold(total) { sum, asset ->
                            sum + (asset.quantity?.toBigIntegerOrNull() ?: BigInteger.ZERO)
                        }

                // A short page is the last one; a full page means there may be more rows.
                if (assets.size < KOIOS_PAGE_SIZE) {
                    walkedToLastPage = true
                    break
                }
            }
            // Every page came back full, so the walk never proved it read the whole holding: the
            // asset may sit past the ceiling. Fail rather than hand back a zero or an undercount.
            check(walkedToLastPage) {
                "Cardano address_assets exceeded ${KOIOS_MAX_PAGES * KOIOS_PAGE_SIZE} rows; " +
                    "${coin.ticker} balance would be incomplete"
            }
            total
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Timber.e("Error in Cardano getTokenBalance : %s", e.message)
            throw e
        }
    }

    override suspend fun getUTXOs(coin: Coin): List<UtxoInfo> {
        val requestBody = CardanoUtxoRequestJson(addresses = listOf(coin.address), extended = true)
        val response =
            httpClient.post(url) {
                url { path(apiV1Path, "address_utxos") }
                setBody(requestBody)
            }

        return try {
            response.bodyOrThrow<List<CardanoUtxoResponseJson>>().toUtxos()
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Timber.e("Error in Cardano getUTXOs : ${e.message}")
            emptyList()
        }
    }

    /**
     * Maps Koios rows to [UtxoInfo], ordered by `(hash, index)`.
     *
     * The order is part of the signed bytes: WalletCore's planner consumes the inputs as given, and
     * the payload this produces is what every co-signer reads. Koios does not promise a stable
     * order, so pinning one here keeps a re-fetch from producing a different keysign session for
     * the same wallet state.
     */
    private fun List<CardanoUtxoResponseJson>.toUtxos(): List<UtxoInfo> =
        mapNotNull { utxo ->
                val assets = utxo.assetList.orEmpty()
                val tokens = assets.mapNotNull { it.toTokenAsset() }
                if (tokens.size != assets.size) {
                    // A half-read UTxO would understate its bundle, and signing that builds a body
                    // that does not conserve the assets it spends. Drop the whole UTxO instead.
                    Timber.w("Dropping Cardano UTxO %s: unparseable asset row", utxo.txHash)
                    return@mapNotNull null
                }
                UtxoInfo(
                    hash = utxo.txHash ?: "",
                    amount = utxo.value?.toLong() ?: 0L,
                    index = utxo.txIndex?.toUInt() ?: 0u,
                    // Sorted so the proto serialises identically however Koios ordered the row.
                    cardanoTokens =
                        tokens.sortedWith(compareBy({ it.policyId }, { it.assetNameHex })),
                )
            }
            .sortedWith(compareBy({ it.hash }, { it.index }))

    private fun CardanoAssetResponseJson.toTokenAsset(): CardanoTokenAsset? {
        val amount = quantity?.toBigIntegerOrNull() ?: return null
        if (amount.signum() < 0) return null
        // Validates hex format and length, same contract as the id read back off a Coin's
        // contractAddress — an id this loose would push malformed hex straight into the signing
        // input's TokenAmount.
        val assetId =
            parseCardanoAssetId(cardanoAssetId(policyId ?: return null, assetName.orEmpty()))
                ?: return null
        return CardanoTokenAsset(
            policyId = assetId.policyId,
            assetNameHex = assetId.assetNameHex,
            amount = amount,
        )
    }

    override suspend fun broadcastTransaction(chain: String, signedTransaction: String): String? {
        return try {
            val payload = buildJsonObject {
                put("jsonrpc", "2.0")
                put("method", "submitTransaction")
                put(
                    "params",
                    buildJsonObject {
                        put("transaction", buildJsonObject { put("cbor", signedTransaction) })
                    },
                )
                put("id", 1)
            }

            val response = httpClient.post(ogmiosUrl) { setBody(payload) }

            when (response.status) {
                HttpStatusCode.OK -> {
                    val ogmiosResponse = response.bodyOrThrow<OgmiosTransactionResponse>()

                    ogmiosResponse.result?.transaction?.id
                        ?: run {
                            // Ogmios can convey a submission error under HTTP 200. Mirror the 400
                            // handling so a duplicate-broadcast rejection recovers regardless of
                            // the
                            // status Ogmios uses to report it.
                            val submitError = ogmiosResponse.error
                            val errorMessage =
                                submitError?.data?.error ?: submitError?.message ?: "Unknown error"
                            Timber.e("Cardano transaction submission failed: $errorMessage")
                            if (submitError?.isAlreadyBroadcast() == true) {
                                throw CardanoTransactionAlreadyBroadcastException(errorMessage)
                            }
                            error("Failed to broadcast transaction: $errorMessage")
                        }
                }

                HttpStatusCode.BadRequest -> {
                    // Never report success from the error payload itself: the txid inside
                    // unknownOutputReferences is the PARENT tx that created the spent input, not
                    // the tx we broadcast. Duplicate rejections throw the typed exception so
                    // BroadcastTxUseCase recovers to OUR locally computed hash; everything else
                    // throws generically and falls through to on-chain verification.
                    val ogmiosError =
                        json.decodeFromString<OgmiosTransactionResponse>(response.bodyAsText())
                    val submitError = ogmiosError.error
                    val errorMessage =
                        submitError?.data?.error ?: submitError?.message ?: "Unknown error"
                    Timber.e("Cardano transaction submission failed: $errorMessage")
                    if (submitError?.isAlreadyBroadcast() == true) {
                        throw CardanoTransactionAlreadyBroadcastException(errorMessage)
                    }
                    error("Failed to broadcast transaction: $errorMessage")
                }

                else -> {
                    Timber.e("Failed to broadcast Cardano transaction: ${response.status}")
                    error("Failed to broadcast transaction: ${response.status}")
                }
            }
        } catch (t: Throwable) {
            if (t is CancellationException) throw t
            if (t is CardanoTransactionAlreadyBroadcastException) throw t
            // Let fatal errors (OOM, StackOverflow) propagate rather than masking them as a
            // broadcast rejection.
            if (t is Error) throw t
            Timber.e(t, "Failed to broadcast Cardano transaction")
            error("Failed to broadcast transaction : ${t.message}")
        }
    }

    // Ogmios reports the loser of a duplicate-broadcast race in two stages, depending on how far
    // the winner's byte-identical tx has progressed:
    //  - mempool stage: 3997/UnexpectedMempoolError with "All inputs are spent. Transaction has
    //    probably already been included"
    //  - post-inclusion: 3117/UnknownOutputReference — the inputs no longer exist on the ledger
    //    because the winner's tx consumed them.
    // iOS (CardanoService.swift) and the shared SDK (broadcast/resolvers/cardano.ts) both treat
    // 3117 as the duplicate signal against this same backend.
    private fun OgmiosError.isAlreadyBroadcast(): Boolean =
        code == OGMIOS_UNKNOWN_OUTPUT_REFERENCE_CODE ||
            data?.error?.contains("already been included", ignoreCase = true) == true ||
            data?.error?.contains("inputs are spent", ignoreCase = true) == true

    private suspend fun getCurrentSlot(): ULong {
        val response = httpClient.get(url) { url { path(apiV1Path, "tip") } }

        if (response.status != HttpStatusCode.OK) {
            val responseString = response.bodyAsText()
            Timber.d("Failed to parse slot from response: $responseString")
            error("Failed to parse slot from response: $responseString")
        }
        val cardanoSlotResponse = response.bodyOrThrow<List<CardanoSlotResponseJson>>()
        return cardanoSlotResponse.firstOrNull()?.absSlot?.toULong() ?: 0UL
    }

    override suspend fun calculateDynamicTTL(): ULong {
        val currentSlot = getCurrentSlot()
        return currentSlot + 720u // Add 720 slots (~12 minutes at 1 slot per second)
    }

    override suspend fun getTxStatus(txHash: String): CardanoTxStatusResponseJson? {
        val requestBody = mapOf("_tx_hashes" to listOf(txHash))
        val response =
            httpClient.post(url) {
                url { path(apiV1Path, "tx_status") }
                setBody(requestBody)
            }
        return response.bodyOrThrow<List<CardanoTxStatusResponseJson>>().firstOrNull()
    }
}

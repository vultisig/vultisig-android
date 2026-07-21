package com.vultisig.wallet.data.api

import com.vultisig.wallet.data.api.models.cardano.CardanoBalanceResponseJson
import com.vultisig.wallet.data.api.models.cardano.CardanoSlotResponseJson
import com.vultisig.wallet.data.api.models.cardano.CardanoTxStatusResponseJson
import com.vultisig.wallet.data.api.models.cardano.CardanoUtxoRequestJson
import com.vultisig.wallet.data.api.models.cardano.CardanoUtxoResponseJson
import com.vultisig.wallet.data.api.models.cardano.OgmiosTransactionResponse
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.payload.UtxoInfo
import com.vultisig.wallet.data.utils.bodyOrThrow
import io.ktor.client.HttpClient
import io.ktor.client.request.get
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
            BigInteger.ZERO
        }
    }

    override suspend fun getUTXOs(coin: Coin): List<UtxoInfo> {
        val requestBody = CardanoUtxoRequestJson(listOf(coin.address))
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

    private fun List<CardanoUtxoResponseJson>.toUtxos() = map { utxo ->
        UtxoInfo(
            hash = utxo.txHash ?: "",
            amount = utxo.value?.toLong() ?: 0L,
            index = utxo.txIndex?.toUInt() ?: 0u,
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
                            val errorMessage = ogmiosResponse.error?.message ?: "Unknown error"
                            Timber.e("Cardano transaction submission failed: $errorMessage")
                            error("Failed to broadcast transaction: $errorMessage")
                        }
                }

                HttpStatusCode.BadRequest -> {
                    // A 400 (e.g. unknownOutputReferences) is a genuine rejection. The txid inside
                    // unknownOutputReferences is the PARENT tx that created the spent input, not
                    // the
                    // tx we broadcast — returning it would report success under an unrelated hash.
                    // Throw instead so BroadcastTxUseCase can verify our actual hash on-chain and
                    // only treat a real duplicate submission as success.
                    val ogmiosError =
                        json.decodeFromString<OgmiosTransactionResponse>(response.bodyAsText())
                    val dataError = ogmiosError.error?.data?.error
                    val errorMessage = dataError ?: ogmiosError.error?.message ?: "Unknown error"
                    Timber.e("Cardano transaction submission failed: $errorMessage")
                    if (dataError?.isAlreadyBroadcast() == true) {
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
            Timber.e(t, "Failed to broadcast Cardano transaction")
            error("Failed to broadcast transaction : ${t.message}")
        }
    }

    // Ogmios reports spent inputs as "All inputs are spent. Transaction has probably already been
    // included" — the loser of a duplicate-broadcast race, not a genuine rejection.
    private fun String.isAlreadyBroadcast(): Boolean =
        contains("already been included", ignoreCase = true) ||
            contains("inputs are spent", ignoreCase = true)

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

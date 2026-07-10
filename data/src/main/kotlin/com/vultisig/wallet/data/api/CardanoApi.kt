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
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import timber.log.Timber

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

        repeat(SUBMIT_MAX_ATTEMPTS) { attempt ->
            if (attempt > 0) delay(SUBMIT_RETRY_BACKOFF_MS)
            try {
                val response = httpClient.post(ogmiosUrl) { setBody(payload) }
                val ogmiosResponse =
                    when (response.status) {
                        HttpStatusCode.OK -> response.bodyOrThrow<OgmiosTransactionResponse>()
                        HttpStatusCode.BadRequest ->
                            json.decodeFromString<OgmiosTransactionResponse>(response.bodyAsText())
                        else -> {
                            Timber.e("Failed to broadcast Cardano transaction: ${response.status}")
                            error("Failed to broadcast transaction: ${response.status}")
                        }
                    }

                ogmiosResponse.result?.transaction?.id?.let {
                    return it
                }

                // Ogmios 3005 (era boundary, near a hard-fork) is transient; it advises retrying.
                if (
                    ogmiosResponse.error?.code == OGMIOS_ERROR_ERA_BOUNDARY &&
                        attempt < SUBMIT_MAX_ATTEMPTS - 1
                ) {
                    Timber.w(
                        "Cardano submit hit era boundary (3005); retrying %d/%d",
                        attempt + 1,
                        SUBMIT_MAX_ATTEMPTS,
                    )
                    return@repeat
                }

                // Any other failure (incl. unknownOutputReferences, whose txid is the PARENT tx
                // that
                // created the spent input, not the tx we broadcast) is a genuine rejection. Throw
                // so
                // BroadcastTxUseCase verifies our actual hash on-chain rather than reporting
                // success
                // under an unrelated hash.
                val errorMessage = ogmiosResponse.error?.message ?: "Unknown error"
                Timber.e("Cardano transaction submission failed: $errorMessage")
                error("Failed to broadcast transaction: $errorMessage")
            } catch (t: CancellationException) {
                throw t
            } catch (t: IllegalStateException) {
                // Already-formatted rejection from the error() calls above — surface as-is.
                throw t
            } catch (t: Throwable) {
                Timber.e(t, "Failed to broadcast Cardano transaction")
                error("Failed to broadcast transaction : ${t.message}")
            }
        }
        error(
            "Failed to broadcast transaction: era boundary persisted after $SUBMIT_MAX_ATTEMPTS attempts"
        )
    }

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

    private companion object {
        // Ogmios SubmitTransactionFailure<EraMismatch>: transient near an era boundary/hard-fork.
        const val OGMIOS_ERROR_ERA_BOUNDARY = 3005
        const val SUBMIT_MAX_ATTEMPTS = 3
        const val SUBMIT_RETRY_BACKOFF_MS = 2_000L
    }
}

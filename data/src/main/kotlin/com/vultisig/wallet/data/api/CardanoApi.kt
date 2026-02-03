package com.vultisig.wallet.data.api

import com.vultisig.wallet.data.api.models.cardano.CardanoBalanceResponseJson
import com.vultisig.wallet.data.api.models.cardano.CardanoSlotResponseJson
import com.vultisig.wallet.data.api.models.cardano.CardanoTxStatusResponseJson
import com.vultisig.wallet.data.api.models.cardano.CardanoUtxoRequestJson
import com.vultisig.wallet.data.api.models.cardano.CardanoUtxoResponseJson
import com.vultisig.wallet.data.api.models.cardano.OgmiosTransactionResponse
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.payload.UtxoInfo
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.http.path
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import timber.log.Timber
import java.math.BigInteger
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException

interface CardanoApi {
    suspend fun getBalance(coin: Coin): BigInteger
    suspend fun getUTXOs(coin: Coin): List<UtxoInfo>
    suspend fun getTxStatus(txHash: String): CardanoTxStatusResponseJson?
    suspend fun calculateDynamicTTL(): ULong
    suspend fun broadcastTransaction(chain: String, signedTransaction: String): String?
}

internal class CardanoApiImpl @Inject constructor(
    private val httpClient: HttpClient,
) : CardanoApi {
    private val url: String = "https://api.koios.rest"
    private val apiV1Path: String = "api/v1"
    private val url2 = "https://api.vultisig.com"
    private val ogmiosUrl = "https://api.vultisig.com/ada/"

    override suspend fun getBalance(coin: Coin): BigInteger {

        val requestBody = mapOf("_addresses" to listOf(coin.address))
        val response = httpClient.post(url) {
            url {
                path(
                    apiV1Path,
                    "address_info"
                )
            }
            setBody(requestBody)
        }
        return try {
            val balances: List<CardanoBalanceResponseJson> = response.body()
            val balanceString = balances.firstOrNull()?.balance ?: "0"
            BigInteger(balanceString)
        } catch (e: Exception) {
            Timber.e("Error in Cardano getBalance : ${e.message}")
            BigInteger.ZERO
        }
    }

    override suspend fun getUTXOs(coin: Coin): List<UtxoInfo> {
        val requestBody = CardanoUtxoRequestJson(listOf(coin.address))
        val response = httpClient.post(url) {
            url {
                path(
                    apiV1Path,
                    "address_utxos"
                )
            }
            setBody(requestBody)
        }

        return try {
            response.body<List<CardanoUtxoResponseJson>>().toUtxos()
        } catch (e: Exception) {
            Timber.e("Error in Cardano getUTXOs : ${e.message}")
            emptyList()
        }
    }

    private fun List<CardanoUtxoResponseJson>.toUtxos() = map { utxo ->
        UtxoInfo(
            hash = utxo.txHash ?: "",
            amount = utxo.value?.toLong() ?: 0L,
            index = utxo.txIndex?.toUInt() ?: 0u
        )
    }

    override suspend fun broadcastTransaction(chain: String, signedTransaction: String): String? {
        return try {
            val payload = buildJsonObject {
                put("jsonrpc", "2.0")
                put("method", "submitTransaction")
                put("params", buildJsonObject {
                    put("transaction", buildJsonObject {
                        put("cbor", signedTransaction)
                    })
                })
                put("id", 1)
            }
            
            val response = httpClient.post(ogmiosUrl) {
                setBody(payload)
            }

            when (response.status) {
                HttpStatusCode.OK -> {
                    val ogmiosResponse = response.body<OgmiosTransactionResponse>()
                    
                    ogmiosResponse.result?.transaction?.id ?: run {
                        val errorMessage = ogmiosResponse.error?.message ?: "Unknown error"
                        Timber.e("Cardano transaction submission failed: $errorMessage")
                        error("Failed to broadcast transaction: $errorMessage")
                    }
                }
                else -> {
                    Timber.e("Failed to broadcast Cardano transaction: ${response.status}")
                    error("Failed to broadcast transaction: ${response.status}")
                }
            }
        } catch (t: Throwable) {
            if (t is CancellationException) throw t
            Timber.e(t, "Failed to broadcast Cardano transaction")
            error("Failed to broadcast transaction : ${t.message}")
        }
    }

    /* override suspend fun broadcastTransaction(
        chain: String, signedTransaction: String,
    ): String? {
        return try {
            val response = httpClient.post(url2) {
                url {
                    path(
                        "blockchair",
                        "cardano",
                        "push",
                        "transaction"
                    )
                }
                setBody(CardanoTransactionHashRequestBodyJson(signedTransaction))
            }

            if (response.status != HttpStatusCode.OK) {
                val responseString = response.body<String>()
                if (responseString.contains("BadInputsUTxO") || responseString.contains("timed out")) {
                    Timber.d("Cardano transaction already broadcast")
                    return null
                }
                Timber.d("Failed to broadcast transaction: $responseString")
                error("Failed to broadcast transaction: $responseString")
            }

            val cardanoBroadcastResponse: CardanoBroadcastResponseJson = response.body()
            cardanoBroadcastResponse.data.transactionHash
        } catch (e: Exception) {
            error("Failed to broadcast transaction: ${e.message}")
        }
    } */

    private suspend fun getCurrentSlot(): ULong {
        val response = httpClient.get(url) {
            url {
                path(
                    apiV1Path,
                    "tip"
                )
            }
        }

        if (response.status != HttpStatusCode.OK) {
            val responseString = response.bodyAsText()
            Timber.d("Failed to parse slot from response: $responseString")
            error("Failed to parse slot from response: $responseString")
        }
        val cardanoSlotResponse: List<CardanoSlotResponseJson> = response.body()
        return cardanoSlotResponse.firstOrNull()?.absSlot?.toULong() ?: 0UL
    }

    override suspend fun calculateDynamicTTL(): ULong {
        val currentSlot = getCurrentSlot()
        return currentSlot + 720u // Add 720 slots (~12 minutes at 1 slot per second)
    }

    override suspend fun getTxStatus(txHash: String): CardanoTxStatusResponseJson? {
        val requestBody = mapOf("_tx_hashes" to listOf(txHash))
        val response = httpClient.post(url) {
            url {
                path(
                    apiV1Path,
                    "tx_status"
                )
            }
            setBody(requestBody)
        }
        return response.body<List<CardanoTxStatusResponseJson>>().firstOrNull()
    }
}



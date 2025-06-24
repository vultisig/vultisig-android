package com.vultisig.wallet.data.api

import com.vultisig.wallet.data.api.models.cardano.CardanoBalanceResponseJson
import com.vultisig.wallet.data.api.models.cardano.CardanoBroadcastResponseJson
import com.vultisig.wallet.data.api.models.cardano.CardanoSlotResponseJson
import com.vultisig.wallet.data.api.models.cardano.CardanoTransactionHashRequestBodyJson
import com.vultisig.wallet.data.api.models.cardano.CardanoUtxoRequestJson
import com.vultisig.wallet.data.api.models.cardano.CardanoUtxoResponseJson
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
import timber.log.Timber
import java.math.BigInteger
import javax.inject.Inject

interface CardanoApi {
    suspend fun getBalance(coin: Coin): BigInteger
    suspend fun getUTXOs(coin: Coin): List<UtxoInfo>
    suspend fun calculateDynamicTTL(): ULong
    suspend fun broadcastTransaction(chain: String, signedTransaction: String): String?
}

internal class CardanoApiImpl @Inject constructor(
    private val httpClient: HttpClient,
) : CardanoApi {
    private val url: String = "https://api.koios.rest"
    private val apiV1Path: String = "api/v1"
    private val url2 = "https://api.vultisig.com"

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

    override suspend fun broadcastTransaction(
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

    }

    suspend fun getCurrentSlot(): ULong {
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
}



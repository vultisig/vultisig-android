package com.vultisig.wallet.data.api

import com.vultisig.wallet.data.api.models.TransactionHashRequestBodyJson
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.payload.BlockChainSpecific
import com.vultisig.wallet.data.models.payload.UtxoInfo
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.path
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.json.JSONObject
import timber.log.Timber
import java.math.BigInteger
import javax.inject.Inject

interface CardanoApi {
    suspend fun getBalance(coin: Coin): BigInteger
    suspend fun getUTXOs(coin: Coin): List<UtxoInfo>
    suspend fun calculateDynamicTTL(): ULong
    suspend fun broadcastTransaction(chain: String, signedTransaction: String): String
}

internal class CardanoApiImpl @Inject constructor(
    private val json: Json,
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

            accept(ContentType.Application.Json)
        }
        return try {
            val balances: List<CardanoBalanceResponse> = response.body()
            val balanceString = balances.firstOrNull()?.balance ?: "0"
            BigInteger(balanceString)
        } catch (e: Exception) {
            BigInteger.ZERO
        }
    }

    override suspend fun getUTXOs(coin: Coin): List<UtxoInfo> {
        val requestBody = mapOf("_addresses" to listOf(coin.address))
//        return try {
        val response = httpClient.post(url) {
            url {
                path(
                    apiV1Path,
                    "address_utxos"
                )
            }
            setBody(requestBody)
            accept(ContentType.Application.Json)
        }
        return try {
            val cardanoUtxoResponse: List<CardanoUtxoResponse> = response.body()
            var utxoInfos = mutableListOf<UtxoInfo>()
            for (cardanoUtxoResponse: CardanoUtxoResponse in cardanoUtxoResponse) {
                utxoInfos.add(
                    UtxoInfo(
                        hash = cardanoUtxoResponse.txHash ?: "",
                        amount = cardanoUtxoResponse.value?.toLong() ?: 0L,
                        index = cardanoUtxoResponse.txIndex?.toUInt() ?: 0u
                    )
                )
            }
            return utxoInfos
        } catch (e: Exception) {
            emptyList()
        }
    }

    override suspend fun broadcastTransaction(
        chain: String, signedTransaction: String
    ): String {
        return try {
            val postData = mapOf("data" to signedTransaction)

            val response = httpClient.post(url2) {
                url {
                    path(
                        "blockchair",
                        "cardano",
                        "push",
                        "transaction"
                    )

                }
                setBody(postData)
                accept(ContentType.Application.Json)

            }
            if (response.status != HttpStatusCode.OK) {
                Timber.d("fail to broadcast transaction: ${response.bodyAsText()}")
                error("fail to broadcast transaction: ${response.bodyAsText()}")
            }
            return try {
                val cardanoBroadcastResponse: CardanoBroadcastResponse = response.body()
                cardanoBroadcastResponse.data.transactionHash
            } catch (e: Exception) {
                error("Failed to broadcast transaction,error: ${e.message}")
            }

        } catch (e: Exception) {
            error("Failed to broadcast transaction,error: ${e.message}")
        }
    }


    suspend fun getCurrentSlot(): ULong {
        val url = "https://api.koios.rest/api/v1"
        val response = httpClient.get(url) {
            url {
                path(
                    apiV1Path,
                    "tip"
                )
            }
            accept(ContentType.Application.Json)
        }

        if (response.status != HttpStatusCode.OK) {
            Timber.d("Failed to parse slot from response: ${response.bodyAsText()}")
            error("Failed to parse slot from response: ${response.bodyAsText()}")
        }
        val cardanoSlotResponse: List<CardanoSlotResponse> = response.body()
        return cardanoSlotResponse.firstOrNull()?.absSlot?.toULong() ?: 0UL

    }

    override suspend fun calculateDynamicTTL(): ULong {
        val currentSlot = getCurrentSlot()
        return currentSlot + 720u // Add 720 slots (~12 minutes at 1 slot per second)
    }

    suspend fun validateChainSpecific(chainSpecific: BlockChainSpecific) {
        if (chainSpecific !is BlockChainSpecific.Cardano) {
            error("Invalid chain specific type for Cardano")
        }
        val (byteFee, sendMaxAmount, ttl) = chainSpecific
        require(byteFee > 0L) { "Cardano byte fee must be positive" }

        val currentSlot = getCurrentSlot()
        require(ttl > currentSlot) { "Cardano TTL must be greater than current slot" }
    }

    @Serializable
    data class CardanoBalanceResponse(
        @SerialName("balance")
        val balance: String? = null,
    )

    @Serializable
    data class CardanoBroadcastResponse(
        @SerialName("data")
        val data: CardanoBroadcastDataResponse,
    )

    @Serializable
    data class CardanoBroadcastDataResponse(
        @SerialName("transaction_hash")
        val transactionHash: String,
    )


    @Serializable
    data class CardanoUtxoResponse(
        @SerialName("tx_hash")
        val txHash: String? = null,
        @SerialName("tx_index")
        val txIndex: Long? = null,
        @SerialName("value")
        val value: String? = null,
    )

    @Serializable
    data class CardanoSlotResponse(
        @SerialName("abs_slot")
        val absSlot: Long? = null,
    )
}
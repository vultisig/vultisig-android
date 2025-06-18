package com.vultisig.wallet.data.api

import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.payload.BlockChainSpecific
import com.vultisig.wallet.data.models.payload.UtxoInfo
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.path
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.json.JSONObject
import java.math.BigInteger
import javax.inject.Inject

interface CardanoApi {
    suspend fun getBalance(coin: Coin): BigInteger
    suspend fun getUTXOs(coin: Coin): List<UtxoInfo>
    suspend fun broadcastTransaction(chain: String, signedTransaction: String): String
}

internal class CardanoApiImpl @Inject constructor(
    private val httpClient: HttpClient,
) : CardanoApi {

    private val url: String = "https://api.koios.rest/api/v1"
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
                        chain,
                        "push",
                        "transaction"
                    )

                }
                setBody(postData)
                accept(ContentType.Application.Json)

            }

            return try {
                val balances: List<Map<String, Any>> = response.body()
                val json = JSONObject(response.toString())
                val transaction_hash = json
                    .getJSONObject("data")
                    .getString("transaction_hash")


                transaction_hash


            } catch (e: Exception) {
                error("Failed to broadcast transaction,error:(error.localizedDescription)")
            }



        } catch (e: Exception) {
            error("Failed to broadcast transaction,error:(error.localizedDescription)")
        }
    }


    fun estimateTransactionFee(): Int {
        // Use typical Cardano transaction fee range
        // Simple ADA transfers are usually around 170,000-200,000 lovelace (0.17-0.2 ADA)
        // This is much more reliable than trying to calculate from network parameters
        return 180_000 // 0.18 ADA - middle of typical range
    }

    suspend fun getCurrentSlot(): ULong {
        val url = "https://api.koios.rest/api/v1"
        val response: List<Map<String, Any>> = httpClient.get(url) {
            url {
                path("tip")
            }
            accept(ContentType.Application.Json)
        }.body()
        val absSlot = response.firstOrNull()?.get("abs_slot")
            ?: error("Failed to parse slot from response")
        return when (absSlot) {
            is Number -> absSlot.toLong().toULong()
            is String -> absSlot.toULongOrNull() ?: error("Invalid slot value")
            else -> error("Invalid slot value type")
        }
    }

    suspend fun calculateDynamicTTL(): ULong {
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
    data class CardanoUtxoResponse(
        @SerialName("tx_hash")
        val txHash: String? = null,
        @SerialName("tx_index")
        val txIndex: Long? = null,
        @SerialName("value")
        val value: String? = null,
    )
}
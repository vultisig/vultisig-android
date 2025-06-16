package com.vultisig.wallet.data.api

import com.vultisig.wallet.data.api.models.TronTriggerConstantContractJson
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.payload.BlockChainSpecific
import com.vultisig.wallet.data.models.payload.UtxoInfo
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.path
import org.json.JSONObject
import timber.log.Timber
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
    private val rpcUrl: String = "https://api.vultisig.com/ripple"

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
        }.bodyAsText()

        val jsonArray = JSONObject("{\"data\":$response}").getJSONArray("data")

        return try {
            val firstItem = if (jsonArray.length() > 0) jsonArray.getJSONObject(0) else null
            val balanceString = firstItem?.optString(
                "balance",
                "0"
            ) ?: "0"
            BigInteger(balanceString)
        } catch (e: Exception) {
            BigInteger.ZERO
        }
    }

    override suspend fun getUTXOs(coin: Coin): List<UtxoInfo> {
        val requestBody = mapOf("_addresses" to listOf(coin.address))
        return try {
            val response: List<Map<String, Any>> = httpClient.post(url) {
                url {
                    path(
                        apiV1Path,
                        "address_utxos"
                    )
                }
                setBody(requestBody)
                accept(ContentType.Application.Json)
            }.body()
            response.mapNotNull { utxoData ->
                val txHash = utxoData["tx_hash"] as? String
                val txIndex = (utxoData["tx_index"] as? Number)?.toInt()
                val value = utxoData["value"] as? String
                val valueInt = value?.toLongOrNull()
                if (txHash != null && txIndex != null && valueInt != null) {
                    UtxoInfo(
                        hash = txHash,
                        amount = valueInt,
                        index = txIndex.toUInt()
                    )
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    override suspend fun broadcastTransaction(
        chain: String, signedTransaction: String
    ): String {
        return try {

            val postData = mapOf("data" to signedTransaction)

            val response: List<Map<String, Any>> = httpClient.post(rpcUrl) {
                url {
                    path(
                        apiV1Path,
                        "blockchair",
                        "chainName",
                        "push",
                        "transaction"
                    )

                }
                setBody(postData)
                accept(ContentType.Application.Json)

            }.body()

            val json = JSONObject(response.toString())
            val transaction_hash = json
                .getJSONObject("data")
                .getString("transaction_hash")


            transaction_hash
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
        require(byteFee > BigInteger.ZERO) { "Cardano byte fee must be positive" }

        val currentSlot = getCurrentSlot()
        require(ttl > currentSlot) { "Cardano TTL must be greater than current slot" }
    }


}
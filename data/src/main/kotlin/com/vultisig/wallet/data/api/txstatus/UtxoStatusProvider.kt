package com.vultisig.wallet.data.api.txstatus

import com.vultisig.wallet.data.models.Chain
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import javax.inject.Inject

class UtxoStatusProvider @Inject constructor(
    private val httpClient: HttpClient,
    private val json: Json,
) : TransactionStatusProvider {

    private val apiUrls = mapOf(
        Chain.Bitcoin to "https://mempool.space/api/tx",
        Chain.Litecoin to "https://litecoinspace.org/api/tx",
        Chain.Dogecoin to "https://dogechain.info/api/v1/transaction",
        Chain.BitcoinCash to "https://api.blockchair.com/bitcoin-cash/dashboards/transaction",
        Chain.Dash to "https://api.blockchair.com/dash/dashboards/transaction",
        Chain.Zcash to "https://api.blockchair.com/zcash/dashboards/transaction"
    )

    override suspend fun checkStatus(txHash: String, chain: Chain): TransactionResult {
        return try {
            val baseUrl = apiUrls[chain] ?: return TransactionResult.Failed("Unknown chain")
            val response = httpClient.get("$baseUrl/$txHash")

            val body = response.bodyAsText()
            val json = json.parseToJsonElement(body).jsonObject

            val confirmed = when (chain) {
                Chain.Bitcoin, Chain.Litecoin -> {
                    json["status"]?.jsonObject?.get("confirmed")?.jsonPrimitive?.boolean ?: false
                }

                Chain.Dogecoin -> {
                    json["confirmations"]?.jsonPrimitive?.int?.let { it > 0 } ?: false
                }

                Chain.BitcoinCash, Chain.Dash, Chain.Zcash -> {
                    // Blockchair API format
                    val data = json["data"]?.jsonObject?.get(txHash)?.jsonObject
                    val blockId = data?.get("block_id")?.jsonPrimitive?.int
                    blockId != null && blockId > 0
                }

                else -> false
            }

            if (confirmed) {
                TransactionResult.Confirmed
            } else {
                TransactionResult.Pending
            }
        } catch (_: Exception) {
            TransactionResult.NotFound
        }
    }
}
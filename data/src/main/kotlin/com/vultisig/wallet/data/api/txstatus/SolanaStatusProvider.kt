package com.vultisig.wallet.data.api.txstatus

import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.usecases.txstatus.TransactionResult
import com.vultisig.wallet.data.usecases.txstatus.TransactionStatusProvider
import io.ktor.client.HttpClient
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import javax.inject.Inject

class SolanaStatusProvider @Inject constructor(
    private val httpClient: HttpClient,
    private val json: Json,
    ) : TransactionStatusProvider {

    private val rpcUrl = "https://api.mainnet-beta.solana.com"

    override suspend fun checkStatus(txHash: String, chain: Chain): TransactionResult {
        return try {
            val response = httpClient.post(rpcUrl) {
                setBody("""
                    {
                        "jsonrpc": "2.0",
                        "method": "getSignatureStatuses",
                        "params": [["$txHash"]],
                        "id": 1
                    }
                """.trimIndent())
                headers {
                    append("Content-Type", "application/json")
                }
            }

            val body = response.bodyAsText()
            val json = json.parseToJsonElement(body).jsonObject
            val result = json["result"]?.jsonObject?.get("value")?.jsonArray?.get(0)

            if (result == null || result is JsonNull) {
                TransactionResult.NotFound
            } else {
                val status = result.jsonObject
                val confirmationStatus = status["confirmationStatus"]?.jsonPrimitive?.content

                when (confirmationStatus) {
                    "finalized" -> TransactionResult.Confirmed
                    "confirmed", "processed" -> TransactionResult.Pending
                    else -> TransactionResult.NotFound
                }
            }
        } catch (_: Exception) {
            TransactionResult.NotFound
        }
    }
}
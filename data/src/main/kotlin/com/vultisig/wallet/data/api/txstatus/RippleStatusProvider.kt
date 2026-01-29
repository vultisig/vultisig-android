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
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import javax.inject.Inject

class RippleStatusProvider @Inject constructor(
    private val httpClient: HttpClient,
    private val json: Json,
) : TransactionStatusProvider {

    private val rpcUrl = "https://s1.ripple.com:51234"

    override suspend fun checkStatus(txHash: String, chain: Chain): TransactionResult {
        return try {
            val response = httpClient.post(rpcUrl) {
                setBody(
                    """
                    {
                        "method": "tx",
                        "params": [{"transaction": "$txHash"}]
                    }
                """.trimIndent()
                )
                headers {
                    append("Content-Type", "application/json")
                }
            }

            val body = response.bodyAsText()
            val json = json.parseToJsonElement(body).jsonObject
            val result = json["result"]?.jsonObject

            if (result != null) {
                val validated = result["validated"]?.jsonPrimitive?.boolean
                if (validated == true) {
                    TransactionResult.Confirmed
                } else {
                    TransactionResult.Pending
                }
            } else {
                TransactionResult.NotFound
            }
        } catch (_: Exception) {
            TransactionResult.NotFound
        }
    }
}
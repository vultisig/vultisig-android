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
import kotlinx.serialization.json.jsonObject
import javax.inject.Inject

class SuiStatusProvider @Inject constructor(
    private val httpClient: HttpClient,
    private val json: Json,
) : TransactionStatusProvider {

    private val rpcUrl = "https://fullnode.mainnet.sui.io:443"

    override suspend fun checkStatus(txHash: String, chain: Chain): TransactionResult {
        return try {
            val response = httpClient.post(rpcUrl) {
                setBody(
                    """
                    {
                        "jsonrpc": "2.0",
                        "method": "sui_getTransactionBlock",
                        "params": ["$txHash", {"showEffects": true}],
                        "id": 1
                    }
                """.trimIndent()
                )
                headers {
                    append("Content-Type", "application/json")
                }
            }

            val body = response.bodyAsText()
            val json = json.parseToJsonElement(body).jsonObject

            if (json.containsKey("result")) {
                TransactionResult.Confirmed
            } else {
                TransactionResult.NotFound
            }
        } catch (_: Exception) {
            TransactionResult.NotFound
        }
    }
}
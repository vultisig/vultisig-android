package com.vultisig.wallet.data.api.txstatus

import com.vultisig.wallet.data.models.Chain
import io.ktor.client.HttpClient
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import javax.inject.Inject

class PolkadotStatusProvider @Inject constructor(
    private val httpClient: HttpClient,
    private val json: Json,
    ) : TransactionStatusProvider {

    private val apiUrl = "https://polkadot.api.subscan.io/api/scan/extrinsic"

    override suspend fun checkStatus(txHash: String, chain: Chain): TransactionResult {
        return try {
            val response = httpClient.post(apiUrl) {
                setBody("""{"hash": "$txHash"}""")
                headers {
                    append("Content-Type", "application/json")
                }
            }

            val body = response.bodyAsText()
            val json = json.parseToJsonElement(body).jsonObject

            if (json["data"] != null && json["data"] !is JsonNull) {
                val finalized = json["data"]?.jsonObject?.get("finalized")?.jsonPrimitive?.boolean
                if (finalized == true) {
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
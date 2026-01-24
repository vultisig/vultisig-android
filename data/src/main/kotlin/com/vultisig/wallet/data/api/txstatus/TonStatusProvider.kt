package com.vultisig.wallet.data.api.txstatus

import com.vultisig.wallet.data.models.Chain
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import javax.inject.Inject

class TonStatusProvider @Inject constructor(
    private val httpClient: HttpClient,
    private val json: Json,) : TransactionStatusProvider {

    private val apiUrl = "https://toncenter.com/api/v2/getTransactions"

    override suspend fun checkStatus(txHash: String, chain: Chain): TransactionResult {
        return try {
            val response = httpClient.get(apiUrl) {
                parameter("hash", txHash)
            }

            val body = response.bodyAsText()
            val json = json.parseToJsonElement(body).jsonObject
            val result = json["result"]?.jsonArray

            if (!result.isNullOrEmpty()) {
                TransactionResult.Confirmed
            } else {
                TransactionResult.NotFound
            }
        } catch (_: Exception) {
            TransactionResult.NotFound
        }
    }
}
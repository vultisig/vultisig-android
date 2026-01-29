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

class TronStatusProvider @Inject constructor(
    private val httpClient: HttpClient,
    private val json: Json,
) : TransactionStatusProvider {

    private val apiUrl = "https://api.trongrid.io/wallet/gettransactioninfobyid"

    override suspend fun checkStatus(txHash: String, chain: Chain): TransactionResult {
        return try {
            val response = httpClient.post(apiUrl) {
                setBody("""{"value": "$txHash"}""")
                headers {
                    append("Content-Type", "application/json")
                }
            }

            val body = response.bodyAsText()
            val json = json.parseToJsonElement(body).jsonObject

            if (json.containsKey("id")) {
                TransactionResult.Confirmed
            } else {
                TransactionResult.NotFound
            }
        } catch (_: Exception) {
            TransactionResult.NotFound
        }
    }
}
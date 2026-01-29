package com.vultisig.wallet.data.api.txstatus

import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.usecases.txstatus.TransactionResult
import com.vultisig.wallet.data.usecases.txstatus.TransactionStatusProvider
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.headers
import javax.inject.Inject

class CardanoStatusProvider @Inject constructor(private val httpClient: HttpClient) :
    TransactionStatusProvider {

    private val apiUrl = "https://cardano-mainnet.blockfrost.io/api/v0/txs"

    override suspend fun checkStatus(txHash: String, chain: Chain): TransactionResult {
        return try {
            val response = httpClient.get("$apiUrl/$txHash") {
                headers {
                    append("project_id", "BLOCKFROST_API_KEY") //todo
                }
            }

            if (response.status.value == 200) {
                TransactionResult.Confirmed
            } else {
                TransactionResult.NotFound
            }
        } catch (_: Exception) {
            TransactionResult.NotFound
        }
    }
}
package com.vultisig.wallet.data.api.txstatus

import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.usecases.txstatus.TransactionResult
import com.vultisig.wallet.data.usecases.txstatus.TransactionStatusProvider
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import javax.inject.Inject

class ThorMayaChainStatusProvider @Inject constructor(private val httpClient: HttpClient) :
    TransactionStatusProvider {

    private val apiUrls = mapOf(
        Chain.ThorChain to "https://stagenet-thornode.ninerealms.com/thorchain/tx/status",
        Chain.MayaChain to "https://mayanode.mayachain.info/mayachain/tx/status"
    )

    override suspend fun checkStatus(txHash: String, chain: Chain): TransactionResult {
        return try {
            val baseUrl = apiUrls[chain] ?: return TransactionResult.Failed("Unknown chain")
            val response = httpClient.get("$baseUrl/$txHash")

            if (response.status.value == 200) {
                TransactionResult.Confirmed
            } else {
                TransactionResult.Pending
            }
        } catch (e: Exception) {
            TransactionResult.Failed(e.message.toString())
        }
    }
}
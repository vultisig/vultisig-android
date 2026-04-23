package com.vultisig.wallet.data.api.txstatus

import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.usecases.txstatus.TransactionResult
import com.vultisig.wallet.data.usecases.txstatus.TransactionStatusProvider
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import timber.log.Timber

class ThorMayaChainStatusProvider @Inject constructor(private val httpClient: HttpClient) :
    TransactionStatusProvider {

    private val apiUrls =
        mapOf(
            Chain.ThorChain to "https://thornode.thorchain.network/thorchain/tx/status",
            Chain.MayaChain to "https://stagenet.mayanode.mayachain.info/mayachain/tx/status",
        )

    override suspend fun checkStatus(txHash: String, chain: Chain): TransactionResult {
        val baseUrl = apiUrls[chain] ?: return TransactionResult.Failed("Unknown chain")
        return try {
            val response = httpClient.get("$baseUrl/$txHash")
            if (response.status.value == 200) TransactionResult.Confirmed
            else TransactionResult.Pending
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "THOR/Maya status check failed for %s on %s", txHash, chain)
            TransactionResult.Pending
        }
    }
}

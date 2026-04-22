package com.vultisig.wallet.data.api.txstatus

import com.vultisig.wallet.data.api.CosmosApiFactory
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.usecases.txstatus.TransactionResult
import com.vultisig.wallet.data.usecases.txstatus.TransactionStatusProvider
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import timber.log.Timber

class CosmosStatusProvider @Inject constructor(private val cosmosApiFactory: CosmosApiFactory) :
    TransactionStatusProvider {

    override suspend fun checkStatus(txHash: String, chain: Chain): TransactionResult {
        val txResponse =
            try {
                cosmosApiFactory.createCosmosApi(chain).getTxStatus(txHash)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w(e, "Cosmos status check failed for %s on %s", txHash, chain)
                return TransactionResult.Pending
            } ?: return TransactionResult.NotFound

        val response = txResponse.txResponse ?: return TransactionResult.Pending
        val height = response.height?.toLongOrNull() ?: 0L
        if (height <= 0) return TransactionResult.Pending

        return when (response.code) {
            0 -> TransactionResult.Confirmed
            else ->
                TransactionResult.Failed(
                    response.rawLog ?: "Transaction failed with code ${response.code}"
                )
        }
    }
}

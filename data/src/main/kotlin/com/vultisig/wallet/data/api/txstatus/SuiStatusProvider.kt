package com.vultisig.wallet.data.api.txstatus

import com.vultisig.wallet.data.api.chains.SuiApi
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.usecases.txstatus.TransactionResult
import com.vultisig.wallet.data.usecases.txstatus.TransactionStatusProvider
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import timber.log.Timber

class SuiStatusProvider @Inject constructor(private val suiApi: SuiApi) :
    TransactionStatusProvider {

    override suspend fun checkStatus(txHash: String, chain: Chain): TransactionResult {
        // The Sui RPC call was previously unprotected: any HTTP / deserialization failure
        // propagated out of this provider as an uncaught exception.
        // `RefreshPendingTransactionsUseCase`
        // catches and logs it, but the UI surfaces nothing and the row stays stuck in its
        // previous state. Wrap the call so transient failures map to Pending (lets the poller
        // retry) and CancellationException always propagates to preserve structured concurrency.
        val txResponse =
            try {
                suiApi.checkStatus(txHash)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w(e, "Sui tx status check failed for %s — treating as Pending", txHash)
                return TransactionResult.Pending
            }

        return when {
            txResponse == null -> TransactionResult.NotFound
            txResponse.checkpoint == null -> TransactionResult.Pending
            txResponse.effects?.status != null -> {
                when (txResponse.effects.status.status) {
                    "success" -> TransactionResult.Confirmed
                    "failure" ->
                        TransactionResult.Failed(
                            txResponse.effects.status.error ?: "Transaction execution failed"
                        )

                    else -> TransactionResult.Pending
                }
            }

            else -> TransactionResult.Confirmed
        }
    }
}

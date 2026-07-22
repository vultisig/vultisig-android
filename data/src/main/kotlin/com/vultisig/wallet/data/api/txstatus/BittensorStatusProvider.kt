package com.vultisig.wallet.data.api.txstatus

import com.vultisig.wallet.data.api.BittensorApi
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.usecases.txstatus.TransactionResult
import com.vultisig.wallet.data.usecases.txstatus.TransactionStatusProvider
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import timber.log.Timber

internal class BittensorStatusProvider @Inject constructor(private val bittensorApi: BittensorApi) :
    TransactionStatusProvider {

    override suspend fun checkStatus(txHash: String, chain: Chain): TransactionResult =
        try {
            when (bittensorApi.getTxStatus(txHash)?.success) {
                true -> TransactionResult.Confirmed
                false -> TransactionResult.Failed("Transaction failed on Bittensor")
                null -> TransactionResult.Pending
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // The TAO tx-status proxy rate limits aggressively (HTTP 429). Retries within a single
            // request are handled by the shared HttpRequestRetry plugin (GET is idempotent), while
            // backoff across the poll loop's re-hits lives in PollingTxStatusUseCaseImpl. This
            // catch must not add its own delay: checkStatus is shared by BroadcastTxUseCase and
            // RefreshPendingTransactionsUseCase, and a delay here would stall all of them.
            Timber.w(e, "Bittensor status check failed for %s", txHash)
            TransactionResult.Pending
        }
}

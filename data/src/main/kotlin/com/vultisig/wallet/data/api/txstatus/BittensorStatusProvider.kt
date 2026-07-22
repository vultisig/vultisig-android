package com.vultisig.wallet.data.api.txstatus

import com.vultisig.wallet.data.api.BittensorApi
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.usecases.txstatus.TransactionResult
import com.vultisig.wallet.data.usecases.txstatus.TransactionStatusProvider
import com.vultisig.wallet.data.utils.NetworkException
import io.ktor.http.HttpStatusCode
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
            // request are handled by the shared HttpRequestRetry plugin (GET is idempotent). Once
            // those are exhausted, surface the 429 so the poll loop (PollingTxStatusUseCaseImpl)
            // can back off across its re-hits instead of re-hitting at a fixed cadence; adding a
            // delay here is not an option because checkStatus is shared by BroadcastTxUseCase and
            // RefreshPendingTransactionsUseCase and would stall both. Every other failure stays
            // swallowed to Pending so a transient error doesn't mark the tx failed.
            if (e is NetworkException && e.httpStatusCode == HttpStatusCode.TooManyRequests.value) {
                throw e
            }
            Timber.w(e, "Bittensor status check failed for %s", txHash)
            TransactionResult.Pending
        }
}

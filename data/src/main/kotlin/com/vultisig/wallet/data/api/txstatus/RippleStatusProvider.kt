package com.vultisig.wallet.data.api.txstatus

import com.vultisig.wallet.data.api.RippleApi
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.usecases.txstatus.TransactionResult
import com.vultisig.wallet.data.usecases.txstatus.TransactionStatusProvider
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import timber.log.Timber

class RippleStatusProvider @Inject constructor(private val rippleApi: RippleApi) :
    TransactionStatusProvider {
    override suspend fun checkStatus(txHash: String, chain: Chain): TransactionResult {
        // Transient HTTP / deserialization errors map to Pending so the poller retries.
        // CancellationException always propagates.
        return try {
            val tx = rippleApi.getTsStatus(txHash) ?: return TransactionResult.Pending
            if (tx.result.status == "success") {
                TransactionResult.Confirmed
            } else {
                TransactionResult.Failed(tx.result.status)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "Ripple tx status check failed for %s — treating as Pending", txHash)
            TransactionResult.Pending
        }
    }
}

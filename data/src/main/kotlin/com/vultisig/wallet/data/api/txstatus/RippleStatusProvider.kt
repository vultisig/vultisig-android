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

    override suspend fun checkStatus(txHash: String, chain: Chain): TransactionResult =
        try {
            val result = rippleApi.getTsStatus(txHash)?.result
            val engineResult = result?.meta?.transactionResult
            when {
                // Not found yet, or found only in a not-yet-validated ledger: keep polling. A tx in
                // an unvalidated ledger can still be dropped (LastLedgerSequence expiry, failed
                // consensus), so it must not be reported terminally.
                result == null || !result.validated -> TransactionResult.Pending
                // Validated but outcome unknown (meta absent): don't claim success, keep polling.
                engineResult == null -> TransactionResult.Pending
                // Only tesSUCCESS delivered funds. A validated tec* result (tecUNFUNDED_PAYMENT,
                // tecDST_TAG_NEEDED, …) burned the fee and delivered nothing — it is a real
                // failure.
                engineResult == "tesSUCCESS" -> TransactionResult.Confirmed
                else -> TransactionResult.Failed(engineResult)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "Ripple status check failed for %s", txHash)
            TransactionResult.Pending
        }
}

package com.vultisig.wallet.data.api.txstatus

import com.vultisig.wallet.data.api.RippleApi
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.repositories.TransactionHistoryRepository
import com.vultisig.wallet.data.usecases.txstatus.TransactionResult
import com.vultisig.wallet.data.usecases.txstatus.TransactionStatusProvider
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import timber.log.Timber

class RippleStatusProvider
@Inject
constructor(
    private val rippleApi: RippleApi,
    private val transactionHistoryRepository: TransactionHistoryRepository,
) : TransactionStatusProvider {

    override suspend fun checkStatus(txHash: String, chain: Chain): TransactionResult =
        try {
            val result = rippleApi.getTsStatus(txHash)?.result
            val engineResult = result?.meta?.transactionResult
            when {
                // Not found: pending until the validated ledger passes the tx's
                // LastLedgerSequence, after which XRPL can never include it.
                result == null -> checkExpiry(txHash, chain)
                // Found only in a not-yet-validated ledger: keep polling. It can still be dropped
                // (LastLedgerSequence expiry, failed consensus), so it must not be reported
                // terminally.
                result.validated != true -> TransactionResult.Pending
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

    /**
     * The tx's LastLedgerSequence is persisted on its history row at broadcast. Without it (a row
     * recorded before it was stored, or a dApp tx that set none) expiry can't be decided, so the
     * result stays [TransactionResult.Pending].
     */
    private suspend fun checkExpiry(txHash: String, chain: Chain): TransactionResult {
        val lastLedgerSequence =
            transactionHistoryRepository
                .getTransaction(chain.raw, txHash)
                ?.broadcastBlockNumber
                ?.takeIf { it > 0 } ?: return TransactionResult.Pending
        return if (rippleApi.isExpiredPastLastLedger(txHash, lastLedgerSequence)) {
            TransactionResult.Failed(EXPIRED_REASON)
        } else {
            TransactionResult.Pending
        }
    }

    companion object {
        const val EXPIRED_REASON = "Transaction expired: not validated by its LastLedgerSequence"
    }
}

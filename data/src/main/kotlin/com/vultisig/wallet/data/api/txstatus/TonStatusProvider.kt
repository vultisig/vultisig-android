package com.vultisig.wallet.data.api.txstatus

import com.vultisig.wallet.data.api.chains.ton.TonActionPhaseJson
import com.vultisig.wallet.data.api.chains.ton.TonApi
import com.vultisig.wallet.data.api.chains.ton.TonComputePhaseJson
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.usecases.txstatus.TransactionResult
import com.vultisig.wallet.data.usecases.txstatus.TransactionStatusProvider
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import timber.log.Timber

class TonStatusProvider @Inject constructor(private val tonApi: TonApi) :
    TransactionStatusProvider {

    override suspend fun checkStatus(txHash: String, chain: Chain): TransactionResult =
        try {
            val tx = tonApi.getTsStatus(txHash).transactions.firstOrNull()
            val description = tx?.description
            when {
                tx == null -> TransactionResult.NotFound
                description == null -> TransactionResult.Pending
                description.aborted == true -> TransactionResult.Failed("Transaction aborted")
                description.computePhase.hasFailed() ->
                    TransactionResult.Failed(
                        "Compute phase reverted (exit code ${description.computePhase?.exitCode})"
                    )
                description.actionPhase.hasFailed() ->
                    TransactionResult.Failed("Action phase failed — transfer not executed")
                else -> TransactionResult.Confirmed
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "TON status check failed for %s", txHash)
            TransactionResult.Pending
        }
}

/**
 * A TON compute phase signals success with TVM exit codes 0 and 1; any other non-null code is a
 * revert. A `null` exit code means the message had no compute phase (a plain transfer) and is not a
 * failure. Mirrors the iOS/SDK resolver.
 */
private fun TonComputePhaseJson?.hasFailed(): Boolean =
    this?.exitCode?.let { it != 0 && it != 1 } == true

/**
 * The action phase is where the wallet emits its outgoing transfer(s). Since every Vultisig TON
 * send is signed with `IGNORE_ACTION_PHASE_ERRORS`, a transfer that can't be paid for is silently
 * skipped rather than aborting the transaction — the wallet tx lands un-aborted with the seqno
 * consumed but no funds moved. Treat a skipped / unfunded / unsuccessful action phase as a failure
 * so the user isn't shown a false "Confirmed".
 */
private fun TonActionPhaseJson?.hasFailed(): Boolean {
    if (this == null) return false
    return success == false || noFunds == true || (skippedActions ?: 0) > 0
}

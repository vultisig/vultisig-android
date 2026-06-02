package com.vultisig.wallet.data.api.txstatus

import com.vultisig.wallet.data.api.PolkadotApi
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.usecases.txstatus.TransactionResult
import com.vultisig.wallet.data.usecases.txstatus.TransactionStatusProvider
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import timber.log.Timber

class PolkadotStatusProvider @Inject constructor(private val polkadotApi: PolkadotApi) :
    TransactionStatusProvider {

    override suspend fun checkStatus(txHash: String, chain: Chain): TransactionResult =
        try {
            // Substrate has no "get transaction by hash" RPC and Subscan now blocks
            // unauthenticated traffic, so confirmation is done by scanning recent blocks for the
            // extrinsic hash over the same proxied RPC we broadcast through.
            if (polkadotApi.isExtrinsicInChain(txHash, SCAN_DEPTH)) {
                TransactionResult.Confirmed
            } else {
                // Not in a block yet — keep polling until the surrounding poll loop times out.
                TransactionResult.Pending
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "Polkadot status check failed for %s", txHash)
            TransactionResult.Pending
        }

    private companion object {
        // Best head advances ~1 block per poll interval; this window keeps comfortable overlap so a
        // confirmed extrinsic is never skipped between polls, while early-exit keeps the common
        // case
        // (extrinsic at or near the head) to a single block fetch.
        const val SCAN_DEPTH = 10
    }
}

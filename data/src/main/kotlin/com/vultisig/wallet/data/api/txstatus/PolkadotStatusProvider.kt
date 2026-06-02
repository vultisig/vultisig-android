package com.vultisig.wallet.data.api.txstatus

import com.vultisig.wallet.data.api.PolkadotApi
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.repositories.TransactionHistoryRepository
import com.vultisig.wallet.data.usecases.txstatus.TransactionResult
import com.vultisig.wallet.data.usecases.txstatus.TransactionStatusProvider
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import timber.log.Timber

class PolkadotStatusProvider
@Inject
constructor(
    private val polkadotApi: PolkadotApi,
    private val transactionHistoryRepository: TransactionHistoryRepository,
) : TransactionStatusProvider {

    override suspend fun checkStatus(txHash: String, chain: Chain): TransactionResult =
        try {
            // Substrate has no "get transaction by hash" RPC and Subscan now blocks
            // unauthenticated traffic, so confirmation is done by scanning recent blocks for the
            // extrinsic hash over the same proxied RPC we broadcast through.
            if (polkadotApi.isExtrinsicInChain(txHash, scanDepthFor(txHash, chain))) {
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

    /**
     * The inclusion block recedes from the chain head as the head advances, so a fixed head window
     * only works while the foreground poller is hammering right after broadcast. A background
     * refresh that rechecks a still-pending row minutes later would walk a shallow window that no
     * longer reaches the (now buried) inclusion block, leaving a confirmed transfer stuck on
     * `Pending` forever. Scale the scan depth by how long ago the tx was broadcast so the walk
     * always reaches back far enough, bounded so a stuck/dropped extrinsic can't trigger an
     * unbounded scan: once an extrinsic is older than its mortal era it can never be included, so
     * there is nothing to find beyond [MAX_SCAN_DEPTH] blocks from the head.
     */
    private suspend fun scanDepthFor(txHash: String, chain: Chain): Int {
        val broadcastAt =
            runCatching {
                    transactionHistoryRepository.getTransaction(chain.raw, txHash)?.timestamp
                }
                .getOrNull() ?: return MIN_SCAN_DEPTH
        val elapsedBlocks =
            (System.currentTimeMillis() - broadcastAt).coerceAtLeast(0L) / BLOCK_TIME_MS
        return (elapsedBlocks + SCAN_MARGIN_BLOCKS)
            .coerceIn(MIN_SCAN_DEPTH.toLong(), MAX_SCAN_DEPTH.toLong())
            .toInt()
    }

    private companion object {
        // Polkadot relay-chain target block time.
        const val BLOCK_TIME_MS = 6_000L
        // Overlap added on top of the elapsed-block estimate to absorb the few-block inclusion lag
        // and block-time jitter so the inclusion block is never just outside the window.
        const val SCAN_MARGIN_BLOCKS = 10L
        // Floor for the fresh-broadcast / unknown-broadcast-time case (extrinsic at or near the
        // head); keeps the common foreground poll to a couple of block fetches via early-exit.
        const val MIN_SCAN_DEPTH = 10
        // Ceiling that bounds the walk: the 64-block mortal era (after which the extrinsic is
        // permanently invalid) plus the ~50-block foreground poll window, with margin. A row not
        // confirmed within this window is dropped, not still confirmable, so a deeper scan would
        // only re-fetch buried history for nothing.
        const val MAX_SCAN_DEPTH = 130
    }
}

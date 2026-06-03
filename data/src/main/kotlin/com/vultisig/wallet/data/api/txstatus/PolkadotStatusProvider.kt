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

    // Substrate has no "get transaction by hash" RPC and Subscan now blocks unauthenticated
    // traffic, so confirmation is done by scanning blocks for the extrinsic hash over the same
    // proxied RPC we broadcast through.
    override suspend fun checkStatus(txHash: String, chain: Chain): TransactionResult =
        try {
            val broadcastBlock =
                runCatching {
                        transactionHistoryRepository
                            .getTransaction(chain.raw, txHash)
                            ?.broadcastBlockNumber
                    }
                    .getOrNull()
            if (broadcastBlock != null) {
                checkByInclusionWindow(txHash, broadcastBlock)
            } else {
                // Rows recorded before the broadcast block was persisted (legacy/non-Polkadot
                // paths) fall back to the head-relative scan scaled by broadcast age.
                checkByHeadWindow(txHash, chain)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "Polkadot status check failed for %s", txHash)
            TransactionResult.Pending
        }

    /**
     * Anchor the scan to the absolute block captured at broadcast and walk the mortal-era window
     * `[broadcastBlock, broadcastBlock + INCLUSION_WINDOW_BLOCKS]`. The window is fixed and
     * age-independent, so a confirmed transfer is found no matter how long after broadcast the
     * check runs — unlike a head-relative scan, whose window slides past the (now buried) inclusion
     * block. The signed extrinsic is mortal: once the head advances past the window (plus a
     * finality margin) without inclusion it can never be included, so the result becomes terminal
     * [Failed] instead of polling [Pending] forever.
     */
    private suspend fun checkByInclusionWindow(
        txHash: String,
        broadcastBlock: Long,
    ): TransactionResult {
        val head = polkadotApi.getBlockHeader().toLong()
        val windowEnd = broadcastBlock + INCLUSION_WINDOW_BLOCKS
        val toBlock = minOf(head, windowEnd)
        if (polkadotApi.isExtrinsicInBlockRange(txHash, broadcastBlock, toBlock)) {
            return TransactionResult.Confirmed
        }
        return if (head > windowEnd + FINALITY_MARGIN_BLOCKS) {
            TransactionResult.Failed("Extrinsic expired: not included within its mortal era")
        } else {
            TransactionResult.Pending
        }
    }

    private suspend fun checkByHeadWindow(txHash: String, chain: Chain): TransactionResult =
        if (polkadotApi.isExtrinsicInChain(txHash, scanDepthFor(txHash, chain))) {
            TransactionResult.Confirmed
        } else {
            // Not in a block yet — keep polling until the surrounding poll loop times out.
            TransactionResult.Pending
        }

    /**
     * The inclusion block recedes from the chain head as the head advances, so a fixed head window
     * only works while the foreground poller is hammering right after broadcast. Scale the scan
     * depth by how long ago the tx was broadcast so the walk always reaches back far enough,
     * bounded by [MAX_SCAN_DEPTH] so a stuck/dropped extrinsic can't trigger an unbounded scan.
     * Used only for legacy rows without a persisted broadcast block; new rows use the absolute
     * inclusion window in [checkByInclusionWindow].
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
        // Mortal-era period the signed extrinsic is built with (PolkadotHelper sets period = 64):
        // the extrinsic is valid for inclusion only within this many blocks of its checkpoint, so
        // scanning beyond the window can never find it.
        const val INCLUSION_WINDOW_BLOCKS = 64L
        // Extra blocks past the window before declaring a miss terminal, to absorb head-read
        // staleness and the few-block inclusion lag so a just-included extrinsic isn't failed
        // early.
        const val FINALITY_MARGIN_BLOCKS = 10L

        // --- legacy head-relative fallback ---
        // Polkadot relay-chain target block time.
        const val BLOCK_TIME_MS = 6_000L
        // Overlap on top of the elapsed-block estimate to absorb inclusion lag and block-time
        // jitter.
        const val SCAN_MARGIN_BLOCKS = 10L
        // Floor for the fresh-broadcast / unknown-broadcast-time case (extrinsic at or near head).
        const val MIN_SCAN_DEPTH = 10
        // Ceiling that bounds the walk: the 64-block mortal era plus the foreground poll window
        // with
        // margin. A row not confirmed within this window is no longer confirmable.
        const val MAX_SCAN_DEPTH = 130
    }
}

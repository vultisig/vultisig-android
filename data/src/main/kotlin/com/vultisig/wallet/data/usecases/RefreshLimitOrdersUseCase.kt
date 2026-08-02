package com.vultisig.wallet.data.usecases

import com.vultisig.wallet.data.api.ThorChainApi
import com.vultisig.wallet.data.api.models.thorchain.ThorchainLimitSwapQueueEntry
import com.vultisig.wallet.data.api.txstatus.LimitOrderOutcome
import com.vultisig.wallet.data.api.txstatus.MidgardLimitOutcomeResolver
import com.vultisig.wallet.data.api.txstatus.thorchainTxId
import com.vultisig.wallet.data.db.models.PendingLimitOrderEntity
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.LimitOrderStatus
import com.vultisig.wallet.data.repositories.PendingLimitOrderRepository
import com.vultisig.wallet.data.usecases.txstatus.TransactionResult
import com.vultisig.wallet.data.usecases.txstatus.TransactionStatusRepository
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber

/**
 * Reconciles the vault's resting limit orders against THORChain's advanced swap queue.
 *
 * Deliberately unlike the swap trackers this codebase already has, for three reasons:
 * 1. **It polls a LIST, not a hash.** `queue/limit_swaps?sender=` returns every resting order for
 *    an address in one request, so one request serves all of that address's orders.
 * 2. **Absence is the signal.** An order going terminal is never reported; it simply stops being
 *    listed. The queue never says WHY, so the outcome is resolved separately (Midgard) and never
 *    guessed.
 * 3. **It never falls back to the inbound deposit's own status.** Confirming the deposit would
 *    report "successful" for an order that has not filled and may not for another three days. Being
 *    slow is survivable; being wrong is not.
 *
 * A singleton because the absence streaks below are its only in-memory state and have to survive
 * between refreshes.
 */
@Singleton
class RefreshLimitOrdersUseCase
@Inject
constructor(
    private val repository: PendingLimitOrderRepository,
    private val thorChainApi: ThorChainApi,
    private val outcomes: MidgardLimitOutcomeResolver,
    private val transactionStatusRepository: TransactionStatusRepository,
) {

    /**
     * Consecutive polls that have found each order missing from the queue, keyed by inbound tx
     * hash. Absent means "seen resting on the last poll".
     *
     * Only advanced by a poll that actually answered: a network failure or an unrecognised envelope
     * returns before reconciliation, so neither counts as evidence of absence.
     *
     * In memory on purpose. A cold start resets every streak to zero, which can only ever DELAY a
     * closure by one refresh — the safe direction. Persisting it would make a stale backend's blip
     * survive the restart that would otherwise have cleared it.
     */
    private val absentPollStreaks = ConcurrentHashMap<String, Int>()

    /**
     * Cancel transactions this device has already settled a verdict on, so a still-resting order is
     * not re-checked on every refresh for the whole time THORChain takes to drop it from the queue.
     * A failed cancel is never in here: its record is withdrawn on the spot, leaving nothing to
     * re-check.
     */
    private val settledCancelHashes = ConcurrentHashMap.newKeySet<String>()

    /**
     * The poll currently running for each vault, so a second caller can join it instead of starting
     * another. Guarded by [refreshMutex], which is held only across the map lookup — never across
     * the network round-trip.
     */
    private val inFlightByVault = mutableMapOf<String, CompletableDeferred<Unit>>()
    private val refreshMutex = Mutex()

    /**
     * Refreshes are COALESCED per vault: a caller that arrives while a poll is running waits for
     * that poll rather than starting a second one.
     *
     * Not an optimisation. The screen kicks off a poll on entry and pull-to-refresh can fire
     * another a moment later, and two rounds landing milliseconds apart hit the same load-balanced
     * backend — so a single stale "no resting orders" would be counted twice and reach
     * [ABSENT_POLLS_BEFORE_CLOSING] on its own, permanently closing a live order. The threshold
     * only means anything if the polls behind it are independent in time.
     */
    suspend operator fun invoke(vaultId: String) {
        val own = CompletableDeferred<Unit>()
        val running =
            refreshMutex.withLock {
                val existing = inFlightByVault[vaultId]
                if (existing == null) inFlightByVault[vaultId] = own
                existing
            }
        if (running != null) {
            running.join()
            return
        }
        try {
            reconcile(vaultId)
        } finally {
            refreshMutex.withLock { inFlightByVault.remove(vaultId) }
            own.complete(Unit)
        }
    }

    private suspend fun reconcile(vaultId: String) {
        val open = repository.getOpenOrders(vaultId)
        if (open.isEmpty()) return

        open
            .groupBy { it.sourceAddress }
            .forEach { (sender, orders) ->
                if (sender.isNullOrBlank()) {
                    // The queue is addressed by the source-chain sender. With none there is nothing
                    // to poll — leave the orders alone rather than fetch the whole network's queue.
                    Timber.w("%d limit order(s) have no sender address — cannot track", orders.size)
                    return@forEach
                }
                reconcileSender(sender, orders)
            }
    }

    private suspend fun reconcileSender(sender: String, orders: List<PendingLimitOrderEntity>) {
        val resting =
            try {
                thorChainApi.getLimitSwapQueue(sender).limitSwaps
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w(e, "Limit-order queue poll failed")
                return
            }
        if (resting == null) {
            // The `limit_swaps` key was absent: a response we don't understand, NOT an empty queue.
            // Reading it as empty would close every one of this sender's orders at once.
            Timber.e("Limit queue response carried no limit_swaps key — treating as unknown")
            return
        }

        // Both sides go through [thorchainTxId]: hex case is not semantic and the queue's casing
        // need not match the hash we broadcast under, and an EVM hash is stored `0x`-prefixed while
        // the queue reports the bare form — without stripping it, no ETH/AVAX/BSC/BASE order ever
        // matches its own queue entry and Cancel stays blocked on `PlacementNotObserved` for good.
        val restingByHash = resting.associateBy { thorchainTxId(it.swap.tx.id) }

        orders.forEach { order ->
            // Per-ORDER isolation, matching the per-sender isolation the queue fetch above already
            // has: a Room write that throws for one order must not abandon its siblings, nor the
            // remaining senders in the outer loop.
            try {
                val entry = restingByHash[thorchainTxId(order.inboundTxHash)]
                if (entry != null) {
                    // Back in the queue, so any absence recorded earlier was wrong. This reset is
                    // the self-correcting half of the guard, and a reappearance is the
                    // stale-backend signature itself.
                    absentPollStreaks.remove(order.inboundTxHash)
                    verifyPendingCancel(order)
                    observeResting(order, entry)
                } else {
                    observeAbsent(order)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w(e, "Could not reconcile limit order %s", order.inboundTxHash)
            }
        }
    }

    /** Still queued: record the fill split and the expiry countdown, and keep it resting. */
    private suspend fun observeResting(
        order: PendingLimitOrderEntity,
        entry: ThorchainLimitSwapQueueEntry,
    ) {
        val state = entry.swap.state
        try {
            repository.recordObservation(
                inboundTxHash = order.inboundTxHash,
                depositAmount = state?.deposit,
                filledInAmount = state?.inAmount,
                filledOutAmount = state?.outAmount,
                observedTradeTarget = entry.swap.tradeTarget,
                observedSourceAsset = entry.swap.tx.coins?.firstOrNull()?.asset?.memoForm,
                observedTargetAsset = entry.swap.targetAsset?.memoForm,
                // Every numeric field on this endpoint arrives as a string. An unparseable
                // countdown
                // is dropped rather than defaulted: zero would render "expired" on an order that is
                // resting fine.
                timeToExpiryBlocks = entry.timeToExpiryBlocks?.trim()?.toIntOrNull(),
                observedAt = System.currentTimeMillis(),
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "Failed to record a limit-order observation")
        }
    }

    /**
     * Missing from the queue. Absence is this tracker's only terminal signal and it is not always
     * true: the queue is reached through a load-balancing gateway whose backends are not always in
     * sync, and one answering "no resting orders" for an order that never left is a well-formed,
     * present response nothing at the decoding layer can tell from a real closure.
     *
     * So a closure is acted on only once [ABSENT_POLLS_BEFORE_CLOSING] consecutive polls agree.
     * Until then the order keeps whatever state it already has: nothing is written, so the last
     * good resting observation — its fill split and expiry countdown — is left exactly as it was,
     * and no third "possibly gone" state is invented.
     */
    private suspend fun observeAbsent(order: PendingLimitOrderEntity) {
        // Clamped once corroborated: an order can stay absent for many polls without being released
        // — an outcome Midgard has not indexed yet — and the streak is only ever compared against
        // the threshold, so counting past it measures nothing and grows without bound.
        val streak =
            absentPollStreaks.compute(order.inboundTxHash) { _, current ->
                minOf((current ?: 0) + 1, ABSENT_POLLS_BEFORE_CLOSING)
            } ?: 1
        if (streak < ABSENT_POLLS_BEFORE_CLOSING) {
            Timber.i(
                "Limit order %s missing on absent poll %d of %d — not closing it yet",
                order.inboundTxHash,
                streak,
                ABSENT_POLLS_BEFORE_CLOSING,
            )
            return
        }

        // Gone from the queue, so it closed — but the queue never says why. Resolve the outcome; if
        // it is not knowable yet, leave the order resting and ask again. A guess here is permanent:
        // nothing revisits a terminal order.
        val status =
            when (outcomes.resolveOutcome(order.inboundTxHash)) {
                LimitOrderOutcome.Filled -> LimitOrderStatus.Filled
                LimitOrderOutcome.Cancelled -> LimitOrderStatus.Cancelled
                LimitOrderOutcome.Expired -> LimitOrderStatus.Expired
                LimitOrderOutcome.Refunded -> refundedOrCredited(order)
                // Almost always Midgard indexing lag. Stay resting; ask next refresh.
                LimitOrderOutcome.Unresolved -> return
            }
        repository.recordStatus(order.inboundTxHash, status)
        absentPollStreaks.remove(order.inboundTxHash)
    }

    /**
     * A closure the chain gave no reason we recognise for.
     *
     * Credited to this device's cancel only when that cancel was CONFIRMED on its own chain and the
     * order demonstrably could not yet have expired. A broadcast the chain later refuses must never
     * let an unrelated refund be relabelled "cancelled", and neither must a refund that arrives at
     * the end of a TTL that really did elapse.
     *
     * Everything else stays [LimitOrderStatus.Refunded] — the observable fact, with no cause
     * attached. An order rejected at placement (halted pool, bad memo) also refunds, seconds in,
     * and inventing a cause here would be inventing it for that user too.
     *
     * The TTL is approximated from [THORCHAIN_BLOCK_MS], and real block time drifts, so right at
     * the boundary this can read a genuine expiry as a cancel or the reverse. Deliberately
     * tolerated: the two differ only in the label on a closed order — the funds took the same path
     * either way — and the alternative, an exact block height, is not knowable for an order that
     * has already left the queue.
     */
    private fun refundedOrCredited(order: PendingLimitOrderEntity): LimitOrderStatus {
        if (order.cancelBroadcastHash == null || !order.cancelConfirmed) {
            return LimitOrderStatus.Refunded
        }
        val elapsedMs = System.currentTimeMillis() - order.createdAt
        val ttlMs = order.expiryBlocks.toLong() * THORCHAIN_BLOCK_MS
        return if (elapsedMs < ttlMs) LimitOrderStatus.Cancelled else LimitOrderStatus.Refunded
    }

    /**
     * Re-check the cancel transaction recorded against a STILL-RESTING order, and withdraw the
     * record if that transaction failed.
     *
     * The self-heal for the failure a broadcast hash cannot describe: a cancel can be included in a
     * block and still be REFUSED by the handler — THORChain answers `could not find matching limit
     * swap` with a non-zero code — and a record kept on that basis disables the Cancel button for
     * good on an order that is still resting, and pre-labels its eventual closure "cancelled".
     *
     * Only for RESTING orders, and that is not an optimisation. Once an order leaves the queue the
     * record has already done its work, and withdrawing it then would rewrite settled history from
     * a lookup that may simply have been rate-limited.
     */
    private suspend fun verifyPendingCancel(order: PendingLimitOrderEntity) {
        val cancelHash = order.cancelBroadcastHash ?: return
        if (cancelHash in settledCancelHashes) return
        val chain = Chain.entries.firstOrNull { it.raw == order.sourceChain } ?: return

        val result =
            try {
                transactionStatusRepository.checkTransactionStatus(cancelHash, chain)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w(e, "Could not verify a broadcast limit-order cancel")
                return
            }

        when (result) {
            is TransactionResult.Confirmed -> {
                // Persist the confirmation FIRST, so the no-reason refund fallback above may credit
                // this cancel a later closure. Entry into `cancelling` happened on broadcast; this
                // is the only terminal promotion that waits for a verdict.
                repository.confirmCancelBroadcast(order.inboundTxHash, cancelHash)
                // Only now mark it settled. On an L1 route THORChain's verdict is not observable
                // anyway, so re-asking every refresh buys nothing; if the write above threw, the
                // hash is deliberately left unsettled so the next refresh retries it.
                settledCancelHashes.add(cancelHash)
            }

            is TransactionResult.Failed -> {
                Timber.w(
                    "Cancel %s failed on-chain — withdrawing the record: %s",
                    cancelHash,
                    result.reason,
                )
                // Compare-and-set on the hash actually verified: the lookup is a network
                // round-trip,
                // and a cancel recorded in the meantime is a different transaction this verdict
                // says
                // nothing about.
                repository.clearCancelBroadcast(order.inboundTxHash, cancelHash)
            }

            // Not an answer. Ask again next refresh rather than withdraw a record on a rate limit
            // or
            // an indexer that has not caught up.
            else -> Unit
        }
    }

    private companion object {
        /**
         * How many CONSECUTIVE polls must report an order missing before its absence is read as a
         * closure.
         *
         * Two, not three. Three does not defeat what this guards against — a backend that is
         * persistently behind — so the extra poll buys little, while every additional required poll
         * delays a genuine closure. Two closes the single-blip window, which is the one actually
         * observed.
         */
        const val ABSENT_POLLS_BEFORE_CLOSING = 2

        /** THORChain's block time, used only to bound the cancel-credit window. */
        const val THORCHAIN_BLOCK_MS = 6_000L
    }
}

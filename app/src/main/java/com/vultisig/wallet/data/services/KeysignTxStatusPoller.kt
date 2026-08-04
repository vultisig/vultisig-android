package com.vultisig.wallet.data.services

import com.vultisig.wallet.data.api.txstatus.SwapKitTrackingService
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.repositories.TransactionHistoryRepository
import com.vultisig.wallet.data.usecases.txstatus.TransactionResult
import com.vultisig.wallet.data.usecases.txstatus.TxStatusConfigurationProvider
import javax.inject.Inject
import kotlin.coroutines.coroutineContext
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber

/**
 * Foreground SwapKit `/track` poll budget on the done screen. After this the row is left in-flight
 * and handed off to the background tx-history poller, mirroring iOS' 30-minute give-up window.
 */
private const val SWAPKIT_FOREGROUND_TIMEOUT_MS = 30 * 60 * 1000L

/**
 * How long to wait for the status service's binding to connect. The service runs in this process,
 * so a binding that has not connected by now never will — the start was accepted and then dropped.
 */
private val SERVICE_BIND_TIMEOUT = 10.seconds

/**
 * Headroom over the chain's own `maxWaitSeconds` status-poll budget. The service emits
 * [TransactionResult.TimedOut] once it reaches that budget, so this deadline expires only when the
 * service is bound but never polled — never on a healthy poll it would otherwise tear down.
 */
private val STATUS_SERVICE_GRACE = 30.seconds

/**
 * How a done-screen status poll ended. Every observed status already reached the caller through the
 * poll's `onStatus` callback; this says who owns the transaction's status from here on.
 */
enum class TxStatusPollOutcome {
    /** A terminal on-chain status was observed. */
    Terminal,

    /** No status will arrive: the status service never reported, or stopped without settling. */
    NotTracked,

    /**
     * Polling stopped with the transaction still in flight; the background tx-history poller
     * settles it from here.
     */
    HandedOff,
}

/**
 * Drives transaction-status polling for the keysign done screen. Owns the foreground status service
 * lifecycle and the SwapKit settlement loop, and persists each observed status to tx history.
 *
 * Extracted from `KeysignViewModel` so the ViewModel only collects status updates and enriches the
 * EVM fee; the two polling strategies stay distinct here rather than being collapsed into a single
 * parameterized path.
 */
class KeysignTxStatusPoller
@Inject
constructor(
    private val transactionStatusServiceManager: TransactionStatusServiceManager,
    private val swapKitTrackingService: SwapKitTrackingService,
    private val txStatusConfigurationProvider: TxStatusConfigurationProvider,
    private val transactionHistoryRepository: TransactionHistoryRepository,
) {

    /**
     * Polls [txHash] on [chain] for settlement, invoking [onStatus] with each observed
     * [TransactionResult] and persisting it to tx history.
     *
     * A SwapKit-routed swap ([isSwapKitSwap]) settles on its destination leg, which the
     * source-chain status service can't see — gate Success on `/track` instead (parity with the
     * tx-history poller in `RefreshPendingTransactionsUseCase`) so a cross-chain swap (e.g.
     * ETH→SOL) doesn't flip to Success the instant its source-chain deposit confirms.
     */
    suspend fun poll(
        txHash: String,
        chain: Chain,
        isSwapKitSwap: Boolean,
        onStatus: suspend (TransactionResult) -> Unit,
    ): TxStatusPollOutcome =
        if (isSwapKitSwap && swapKitTrackingService.canTrack(chain)) {
            pollSwapKit(txHash, chain, onStatus)
        } else {
            pollStatusService(txHash, chain, onStatus)
        }

    /**
     * Default status poll for the done screen — drives the foreground
     * [TransactionStatusServiceManager] for [txHash] on [chain], emitting each observed status via
     * [onStatus] and persisting it. Every way the service can fail to report — a rejected start, a
     * binding that never connects, a vanished binder, or a bound service that never polls — yields
     * [TxStatusPollOutcome.NotTracked] rather than waiting forever. Tears down the foreground
     * service on every exit path.
     */
    private suspend fun pollStatusService(
        txHash: String,
        chain: Chain,
        onStatus: suspend (TransactionResult) -> Unit,
    ): TxStatusPollOutcome {
        try {
            // A rejected binding never connects, so serviceReady would suspend forever — and a
            // status emitted here would be the last one the screen ever received.
            if (!transactionStatusServiceManager.startPolling(txHash, chain)) {
                return TxStatusPollOutcome.NotTracked
            }
            onStatus(TransactionResult.Pending)
            withTimeoutOrNull(SERVICE_BIND_TIMEOUT) {
                transactionStatusServiceManager.serviceReady.first { it }
            } ?: return TxStatusPollOutcome.NotTracked
            val statusFlow =
                transactionStatusServiceManager.getStatusFlow()
                    ?: return TxStatusPollOutcome.NotTracked
            val budget =
                txStatusConfigurationProvider.getConfigurationForChain(chain).maxWaitSeconds.seconds
            withTimeoutOrNull(budget + STATUS_SERVICE_GRACE) {
                statusFlow
                    .onEach { statusResult ->
                        persistStatus(
                            txHash,
                            chain,
                            statusResult,
                            "Failed to update tx history status for %s",
                        )
                        onStatus(statusResult)
                    }
                    .first { it.isTerminal() }
            } ?: return TxStatusPollOutcome.NotTracked
            return TxStatusPollOutcome.Terminal
        } finally {
            // Tear down the foreground service on every exit path (terminal, early return, or
            // cancellation) so it can't outlive the poll.
            transactionStatusServiceManager.stopPolling()
        }
    }

    /**
     * SwapKit settlement poll for the done screen — calls `/track` on the source [chain]'s
     * broadcast hash and gates Success on the destination-leg status. Runs in the caller's
     * coroutine (no foreground service): when the user leaves the screen the job is cancelled and
     * the tx-history poller ([com.vultisig.wallet.data.usecases.RefreshPendingTransactionsUseCase])
     * takes over. On the timeout the row is left in-flight ([TxStatusPollOutcome.HandedOff]) rather
     * than marked terminal.
     */
    private suspend fun pollSwapKit(
        txHash: String,
        chain: Chain,
        onStatus: suspend (TransactionResult) -> Unit,
    ): TxStatusPollOutcome {
        onStatus(TransactionResult.Pending)
        val pollInterval =
            txStatusConfigurationProvider
                .getConfigurationForChain(chain)
                .pollIntervalSeconds
                .seconds
        val startTime = System.currentTimeMillis()
        while (coroutineContext.isActive) {
            if (System.currentTimeMillis() - startTime >= SWAPKIT_FOREGROUND_TIMEOUT_MS) {
                // Hand off to the background tx-history poller; leave the row pending.
                onStatus(TransactionResult.Pending)
                return TxStatusPollOutcome.HandedOff
            }

            val statusResult = swapKitTrackingService.checkSettlementStatus(txHash, chain)
            persistStatus(
                txHash,
                chain,
                statusResult,
                "Failed to update SwapKit tx history status for %s",
            )
            onStatus(statusResult)
            when (statusResult) {
                TransactionResult.Confirmed,
                is TransactionResult.Failed,
                is TransactionResult.Refunded -> return TxStatusPollOutcome.Terminal

                else -> delay(pollInterval)
            }
        }
        return TxStatusPollOutcome.HandedOff
    }

    /**
     * Best-effort persists [statusResult] to tx history; logs [warnMessage] (single `%s`) on
     * failure.
     */
    private suspend fun persistStatus(
        txHash: String,
        chain: Chain,
        statusResult: TransactionResult,
        warnMessage: String,
    ) {
        runCatching {
                transactionHistoryRepository.updateTransactionStatus(
                    chain = chain.raw,
                    txHash = txHash,
                    result = statusResult,
                )
            }
            .onFailure { Timber.w(it, warnMessage, txHash) }
    }

    /** Stops the foreground status service. */
    fun stopPolling() {
        transactionStatusServiceManager.stopPolling()
    }

    /**
     * Whether this status is settled and polling can stop (confirmed, failed, refunded, timed out).
     */
    private fun TransactionResult.isTerminal(): Boolean =
        when (this) {
            TransactionResult.Confirmed,
            is TransactionResult.Failed,
            is TransactionResult.Refunded,
            TransactionResult.TimedOut -> true

            else -> false
        }
}

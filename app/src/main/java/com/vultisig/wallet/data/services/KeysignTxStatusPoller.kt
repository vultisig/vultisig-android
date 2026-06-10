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
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.isActive
import timber.log.Timber

/**
 * Foreground SwapKit `/track` poll budget on the done screen. After this the row is left in-flight
 * and handed off to the background tx-history poller, mirroring iOS' 30-minute give-up window.
 */
private const val SWAPKIT_FOREGROUND_TIMEOUT_MS = 30 * 60 * 1000L

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
     * [TransactionResult] (starting with [TransactionResult.Pending]) and persisting it to tx
     * history. Returns the terminal result when one is reached, or `null` when polling ends without
     * a terminal status (SwapKit foreground-timeout handoff to the background poller).
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
    ): TransactionResult? =
        if (isSwapKitSwap && swapKitTrackingService.canTrack(chain)) {
            pollSwapKit(txHash, chain, onStatus)
        } else {
            pollStatusService(txHash, chain, onStatus)
        }

    /**
     * Default status poll for the done screen — drives the foreground
     * [TransactionStatusServiceManager] for [txHash] on [chain], emitting each observed status via
     * [onStatus] and persisting it. Returns the first terminal status, or `null` if no status flow
     * is available. Tears down the foreground service on every exit path.
     */
    private suspend fun pollStatusService(
        txHash: String,
        chain: Chain,
        onStatus: suspend (TransactionResult) -> Unit,
    ): TransactionResult? {
        try {
            transactionStatusServiceManager.startPolling(txHash, chain)
            onStatus(TransactionResult.Pending)
            transactionStatusServiceManager.serviceReady
                .filter { it } // Wait until service is ready
                .first()
            val statusFlow = transactionStatusServiceManager.getStatusFlow() ?: return null
            return statusFlow
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
        } finally {
            // Tear down the foreground service on every exit path (terminal, early null return,
            // or cancellation) so it can't outlive the poll.
            transactionStatusServiceManager.stopPolling()
        }
    }

    /**
     * SwapKit settlement poll for the done screen — calls `/track` on the source [chain]'s
     * broadcast hash and gates Success on the destination-leg status. Runs in the caller's
     * coroutine (no foreground service): when the user leaves the screen the job is cancelled and
     * the tx-history poller ([com.vultisig.wallet.data.usecases.RefreshPendingTransactionsUseCase])
     * takes over. On the timeout the row is left in-flight (handed off to that poller, returning
     * `null`) rather than marked terminal.
     */
    private suspend fun pollSwapKit(
        txHash: String,
        chain: Chain,
        onStatus: suspend (TransactionResult) -> Unit,
    ): TransactionResult? {
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
                return null
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
                is TransactionResult.Refunded -> return statusResult

                else -> delay(pollInterval)
            }
        }
        return null
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

    /** Releases the foreground status service resources. */
    fun cleanup() {
        transactionStatusServiceManager.cleanup()
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

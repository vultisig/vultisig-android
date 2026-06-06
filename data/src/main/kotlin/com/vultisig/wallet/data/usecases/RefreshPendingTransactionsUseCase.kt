package com.vultisig.wallet.data.usecases

import com.vultisig.wallet.data.IoDispatcher
import com.vultisig.wallet.data.api.txstatus.SwapKitTrackingService
import com.vultisig.wallet.data.db.models.TransactionHistoryEntity
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.SwapProvider
import com.vultisig.wallet.data.models.SwapTransactionHistoryData
import com.vultisig.wallet.data.models.getSwapProviderId
import com.vultisig.wallet.data.repositories.TransactionHistoryRepository
import com.vultisig.wallet.data.usecases.txstatus.TransactionResult
import com.vultisig.wallet.data.usecases.txstatus.TransactionStatusRepository
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import timber.log.Timber

interface RefreshPendingTransactionsUseCase {
    /**
     * Refresh pending transactions for [vaultId]. When [chain] is non-null and non-blank, only
     * transactions on that chain are polled — this avoids hitting indexers for unrelated chains
     * when the user is viewing a single-chain history.
     */
    suspend operator fun invoke(vaultId: String, chain: String? = null)
}

class RefreshPendingTransactionsUseCaseImpl
@Inject
constructor(
    private val transactionHistoryRepository: TransactionHistoryRepository,
    private val transactionStatusRepository: TransactionStatusRepository,
    private val swapKitTrackingService: SwapKitTrackingService,
    @IoDispatcher private val dispatcher: CoroutineDispatcher,
) : RefreshPendingTransactionsUseCase {

    override suspend fun invoke(vaultId: String, chain: String?) {
        val effectiveChain = chain?.takeIf { it.isNotBlank() }
        withContext(dispatcher) {
            transactionHistoryRepository
                .getPendingTransactions(vaultId)
                .filter { effectiveChain == null || it.chain == effectiveChain }
                .filter { it.shouldPollNow() }
                .map { tx -> async { refreshTransaction(tx) } }
                .awaitAll()
        }
    }

    private suspend fun refreshTransaction(tx: TransactionHistoryEntity) {
        val chain =
            resolveChain(tx)
                ?: run {
                    runSafeCatching {
                        transactionHistoryRepository.incrementRetryCount(tx.chain, tx.txHash)
                    }
                    return
                }

        try {
            val result = checkStatus(tx, chain)
            transactionHistoryRepository.updateTransactionStatus(tx.chain, tx.txHash, result)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e, "Failed to refresh %s (retry #%d)", tx.txHash, tx.retryCount)
            runSafeCatching {
                transactionHistoryRepository.incrementRetryCount(tx.chain, tx.txHash)
            }
        }
    }

    /**
     * Resolve the settlement status for [tx]. A SwapKit-routed swap on a `/track`-capable source
     * chain is gated on the destination-leg settlement (`/track`) — the same way THORChain/Maya
     * swaps are gated through `ThorMayaChainStatusProvider` — so a cross-chain swap (e.g. ETH→SOL)
     * isn't marked Success the instant its source-chain deposit confirms. Every other transaction
     * (sends, same-chain non-SwapKit swaps, SwapKit on a chain `/track` can't address) keeps the
     * existing source-chain-by-tx-hash check unchanged.
     */
    private suspend fun checkStatus(tx: TransactionHistoryEntity, chain: Chain): TransactionResult {
        val isSwapKitSwap =
            (tx.payload as? SwapTransactionHistoryData)?.provider ==
                SwapProvider.SWAPKIT.getSwapProviderId()
        return if (isSwapKitSwap && swapKitTrackingService.canTrack(chain)) {
            swapKitTrackingService.checkSettlementStatus(tx.txHash, chain)
        } else {
            transactionStatusRepository.checkTransactionStatus(tx.txHash, chain)
        }
    }

    private suspend fun resolveChain(tx: TransactionHistoryEntity): Chain? =
        try {
            Chain.fromRaw(tx.chain)
        } catch (_: NoSuchElementException) {
            Timber.w("Unknown chain '%s' for tx %s — retiring", tx.chain, tx.txHash)
            runSafeCatching {
                transactionHistoryRepository.updateTransactionStatus(
                    chain = tx.chain,
                    txHash = tx.txHash,
                    result = TransactionResult.Failed("Unknown chain: ${tx.chain}"),
                )
            }
            null
        }

    /**
     * Exponential backoff: skip this poll cycle if not enough time has elapsed since the last
     * check, scaled by the retry count.
     *
     * retryCount 0 -> always poll (no backoff) retryCount 1 -> wait >= 30s retryCount 2 -> wait >=
     * 60s retryCount N -> wait >= min(30s * 2^(N-1), 10min)
     */
    private fun TransactionHistoryEntity.shouldPollNow(): Boolean {
        if (retryCount == 0) return true
        val lastChecked = lastCheckedAt ?: return true
        val backoffMs =
            (BASE_BACKOFF_MS shl (retryCount - 1).coerceAtMost(MAX_SHIFT)).coerceAtMost(
                MAX_BACKOFF_MS
            )
        val elapsed = (System.currentTimeMillis() - lastChecked).coerceAtLeast(0L)
        return elapsed >= backoffMs
    }

    private companion object {
        const val BASE_BACKOFF_MS = 30_000L
        const val MAX_BACKOFF_MS = 600_000L
        const val MAX_SHIFT = 13
    }
}

/** Like [runCatching] but rethrows [CancellationException] to preserve structured concurrency. */
private inline fun runSafeCatching(block: () -> Unit) {
    try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        // ignored
    }
}

package com.vultisig.wallet.data.usecases

import com.vultisig.wallet.data.IoDispatcher
import com.vultisig.wallet.data.db.models.TransactionHistoryEntity
import com.vultisig.wallet.data.models.Chain
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
    suspend operator fun invoke(vaultId: String)
}

class RefreshPendingTransactionsUseCaseImpl
@Inject
constructor(
    private val transactionHistoryRepository: TransactionHistoryRepository,
    private val transactionStatusRepository: TransactionStatusRepository,
    @IoDispatcher private val dispatcher: CoroutineDispatcher,
) : RefreshPendingTransactionsUseCase {

    override suspend fun invoke(vaultId: String) {
        withContext(dispatcher) {
            transactionHistoryRepository
                .getPendingTransactions(vaultId)
                .filter { it.shouldPollNow() }
                .map { tx -> async { refreshTransaction(tx) } }
                .awaitAll()
        }
    }

    private suspend fun refreshTransaction(tx: TransactionHistoryEntity) {
        val chain =
            resolveChain(tx)
                ?: run {
                    runSafeCatching { transactionHistoryRepository.incrementRetryCount(tx.txHash) }
                    return
                }

        try {
            val result = transactionStatusRepository.checkTransactionStatus(tx.txHash, chain)
            transactionHistoryRepository.updateTransactionStatus(tx.txHash, result)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e, "Failed to refresh %s (retry #%d)", tx.txHash, tx.retryCount)
            runSafeCatching { transactionHistoryRepository.incrementRetryCount(tx.txHash) }
        }
    }

    private suspend fun resolveChain(tx: TransactionHistoryEntity): Chain? =
        try {
            Chain.fromRaw(tx.chain)
        } catch (_: NoSuchElementException) {
            Timber.w("Unknown chain '%s' for tx %s — retiring", tx.chain, tx.txHash)
            runSafeCatching {
                transactionHistoryRepository.updateTransactionStatus(
                    tx.txHash,
                    TransactionResult.Failed("Unknown chain: ${tx.chain}"),
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

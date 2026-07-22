package com.vultisig.wallet.data.usecases.txstatus

import com.vultisig.wallet.data.models.Chain
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive

fun interface PollingTxStatusUseCase : (Chain, String) -> Flow<TransactionResult>

internal class PollingTxStatusUseCaseImpl
@Inject
constructor(
    private val txStatusConfigurationProvider: TxStatusConfigurationProvider,
    private val transactionStatusRepository: TransactionStatusRepository,
) : PollingTxStatusUseCase {

    override fun invoke(chain: Chain, txHash: String) = flow {
        val config = txStatusConfigurationProvider.getConfigurationForChain(chain)
        val startTime = System.currentTimeMillis()
        val timeoutMillis = config.maxWaitSeconds.seconds.inWholeMilliseconds

        var errorCount = 0
        // Consecutive non-terminal polls (Pending/NotFound or caught errors), drives the backoff.
        // A rate-limited (HTTP 429) status endpoint is swallowed into a Pending result upstream, so
        // the loop never sees it as an error; without growing the interval it would re-hit the
        // endpoint at a fixed cadence and keep it rate limited (issue #5363).
        var backoffAttempt = 0

        while (currentCoroutineContext().isActive) {
            if (System.currentTimeMillis() - startTime >= timeoutMillis) {
                emit(TransactionResult.TimedOut)
                return@flow
            }

            try {
                val result = transactionStatusRepository.checkTransactionStatus(txHash, chain)
                errorCount = 0
                emit(result)

                when (result) {
                    is TransactionResult.Confirmed,
                    is TransactionResult.Failed -> return@flow
                    else -> delay(backoffDelay(config.pollIntervalSeconds, backoffAttempt++))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                errorCount++
                if (errorCount >= MAX_ERRORS) {
                    emit(TransactionResult.Failed("Network error: ${e.message}"))
                    return@flow
                }
                delay(backoffDelay(config.pollIntervalSeconds, backoffAttempt++))
            }
        }
    }

    /**
     * Capped exponential backoff for the poll loop: `pollInterval * 2^attempt`, clamped to
     * [MAX_POLL_BACKOFF]. The first non-terminal poll keeps the configured interval, then the delay
     * doubles so sustained Pending/429 cycles stop hammering the status endpoint at a fixed rate.
     */
    private fun backoffDelay(pollIntervalSeconds: Long, attempt: Int): Duration {
        val multiplier = 1 shl attempt.coerceIn(0, MAX_BACKOFF_SHIFT)
        return minOf(pollIntervalSeconds.seconds * multiplier, MAX_POLL_BACKOFF)
    }

    private companion object {
        const val MAX_ERRORS = 5
        const val MAX_BACKOFF_SHIFT = 5
        val MAX_POLL_BACKOFF = 30.seconds
    }
}

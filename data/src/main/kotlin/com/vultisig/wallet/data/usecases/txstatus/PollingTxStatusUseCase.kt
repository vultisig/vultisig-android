package com.vultisig.wallet.data.usecases.txstatus

import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.utils.NetworkException
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
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
        // Consecutive rate-limited (HTTP 429) or errored polls; drives the exponential backoff so a
        // throttled status endpoint isn't re-hit at a fixed cadence (issue #5363). Reset on every
        // healthy poll: a normal Pending/NotFound result keeps the configured interval, so fast
        // chains (Solana/Ripple/Tron poll every 2s) confirm at their usual cadence instead of
        // ballooning to the backoff cap during an ordinary multi-second confirmation.
        var backoffAttempt = 0

        while (currentCoroutineContext().isActive) {
            if (System.currentTimeMillis() - startTime >= timeoutMillis) {
                emit(TransactionResult.TimedOut)
                return@flow
            }

            try {
                val result = transactionStatusRepository.checkTransactionStatus(txHash, chain)
                errorCount = 0
                backoffAttempt = 0
                emit(result)

                when (result) {
                    is TransactionResult.Confirmed,
                    is TransactionResult.Failed -> return@flow
                    else ->
                        delay(
                            backoffDelay(
                                config.pollIntervalSeconds,
                                attempt = 0,
                                remainingMillis(startTime, timeoutMillis),
                            )
                        )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (e.isRateLimit()) {
                    // A rate-limited (HTTP 429) status endpoint (e.g. the TAO tx-status proxy) is
                    // transient, not a transaction failure: keep polling with exponential backoff,
                    // but don't spend the fatal error budget on it.
                    delay(
                        backoffDelay(
                            config.pollIntervalSeconds,
                            backoffAttempt++,
                            remainingMillis(startTime, timeoutMillis),
                        )
                    )
                } else {
                    errorCount++
                    if (errorCount >= MAX_ERRORS) {
                        emit(TransactionResult.Failed("Network error: ${e.message}"))
                        return@flow
                    }
                    delay(
                        backoffDelay(
                            config.pollIntervalSeconds,
                            backoffAttempt++,
                            remainingMillis(startTime, timeoutMillis),
                        )
                    )
                }
            }
        }
    }

    /** True when [this] or a cause is a rate-limit (HTTP 429) [NetworkException]. */
    private fun Throwable.isRateLimit(): Boolean {
        var current: Throwable? = this
        while (current != null) {
            if (current is NetworkException && current.httpStatusCode == HTTP_TOO_MANY_REQUESTS) {
                return true
            }
            current = current.cause
        }
        return false
    }

    /**
     * Capped exponential backoff for the poll loop: `pollInterval * 2^attempt`, clamped to
     * [MAX_POLL_BACKOFF]. Healthy polls pass `attempt = 0` and keep the configured interval; only
     * sustained rate-limit (429) / error cycles grow `attempt` so they stop hammering the status
     * endpoint at a fixed rate. The delay is further clamped to [remainingMillis] so the loop never
     * sleeps past the polling deadline before emitting [TransactionResult.TimedOut].
     */
    private fun backoffDelay(
        pollIntervalSeconds: Long,
        attempt: Int,
        remainingMillis: Long,
    ): Duration {
        val multiplier = 1 shl attempt.coerceIn(0, MAX_BACKOFF_SHIFT)
        val capped = minOf(pollIntervalSeconds.seconds * multiplier, MAX_POLL_BACKOFF)
        return minOf(capped, remainingMillis.coerceAtLeast(0).milliseconds)
    }

    /** Milliseconds left before the polling deadline; may be zero or negative once elapsed. */
    private fun remainingMillis(startTime: Long, timeoutMillis: Long): Long =
        timeoutMillis - (System.currentTimeMillis() - startTime)

    private companion object {
        const val MAX_ERRORS = 5
        const val MAX_BACKOFF_SHIFT = 5
        const val HTTP_TOO_MANY_REQUESTS = 429
        val MAX_POLL_BACKOFF = 30.seconds
    }
}

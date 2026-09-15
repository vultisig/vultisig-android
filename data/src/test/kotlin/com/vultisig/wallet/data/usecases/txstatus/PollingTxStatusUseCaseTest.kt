package com.vultisig.wallet.data.usecases.txstatus

import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.utils.NetworkException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TestTimeSource
import kotlin.time.TimeSource
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Regression test for issue #5043's "joined device polls a never-committed tx forever" concern. The
 * cadence tests drive real (short) delays via a tiny [TxStatusConfiguration] on the monotonic
 * source; the deadline itself is pinned exactly with a [TestTimeSource] the fake repository
 * advances, so `runTest` can skip the `delay()`s.
 */
class PollingTxStatusUseCaseTest {

    private class AlwaysNotFoundStatusRepository : TransactionStatusRepository {
        var callCount = 0
            private set

        override suspend fun checkTransactionStatus(
            txHash: String,
            chain: Chain,
        ): TransactionResult {
            callCount++
            return TransactionResult.NotFound
        }
    }

    private class AlwaysPendingStatusRepository : TransactionStatusRepository {
        var callCount = 0
            private set

        override suspend fun checkTransactionStatus(
            txHash: String,
            chain: Chain,
        ): TransactionResult {
            callCount++
            return TransactionResult.Pending
        }
    }

    private class AlwaysRateLimitedStatusRepository : TransactionStatusRepository {
        var callCount = 0
            private set

        override suspend fun checkTransactionStatus(
            txHash: String,
            chain: Chain,
        ): TransactionResult {
            callCount++
            throw NetworkException(429, "Too Many Requests")
        }
    }

    private class AlwaysRefundedStatusRepository : TransactionStatusRepository {
        var callCount = 0
            private set

        override suspend fun checkTransactionStatus(
            txHash: String,
            chain: Chain,
        ): TransactionResult {
            callCount++
            return TransactionResult.Refunded("refunded")
        }
    }

    /** Reports Pending and moves [timeSource] forward by one poll interval per check. */
    private class TickingPendingStatusRepository(
        private val timeSource: TestTimeSource,
        private val pollInterval: Duration,
    ) : TransactionStatusRepository {
        var callCount = 0
            private set

        override suspend fun checkTransactionStatus(
            txHash: String,
            chain: Chain,
        ): TransactionResult {
            callCount++
            timeSource += pollInterval
            return TransactionResult.Pending
        }
    }

    private class FakeTxStatusConfigurationProvider(private val config: TxStatusConfiguration) :
        TxStatusConfigurationProvider {
        override fun getConfigurationForChain(chain: Chain) = config

        override fun supportTxStatus(chain: Chain) = true
    }

    @Test
    fun `invoke times out instead of polling forever when the tx is never found`() = runBlocking {
        val repository = AlwaysNotFoundStatusRepository()
        val useCase =
            PollingTxStatusUseCaseImpl(
                txStatusConfigurationProvider =
                    FakeTxStatusConfigurationProvider(
                        TxStatusConfiguration(pollIntervalSeconds = 1, maxWaitSeconds = 2)
                    ),
                transactionStatusRepository = repository,
                timeSource = TimeSource.Monotonic,
            )

        // Bounds the test itself: if this ever regressed back to polling forever on NotFound, this
        // would hang and fail with a TimeoutCancellationException instead of hanging the suite.
        val results = withTimeout(10_000) { useCase(Chain.Qbtc, "deadbeef").toList() }

        assertEquals(TransactionResult.TimedOut, results.last())
        assertTrue(results.dropLast(1).all { it == TransactionResult.NotFound })
        assertTrue(repository.callCount >= 1)
    }

    @Test
    fun `invoke backs off instead of hammering the endpoint on sustained rate limiting`() =
        runBlocking {
            // Regression for issue #5363: a rate-limited (HTTP 429) status endpoint must grow its
            // interval rather than re-hit at a fixed cadence. With a 1s base interval the delays
            // are 1s, 2s, 4s..., so over a ~7s window at most a handful of polls fire — far fewer
            // than the ~7 a fixed 1s interval would produce. A 429 is transient, not a failure, so
            // the loop keeps polling to TimedOut rather than emitting Failed.
            val repository = AlwaysRateLimitedStatusRepository()
            val useCase =
                PollingTxStatusUseCaseImpl(
                    txStatusConfigurationProvider =
                        FakeTxStatusConfigurationProvider(
                            TxStatusConfiguration(pollIntervalSeconds = 1, maxWaitSeconds = 7)
                        ),
                    transactionStatusRepository = repository,
                    timeSource = TimeSource.Monotonic,
                )

            val results = withTimeout(20_000) { useCase(Chain.Bittensor, "deadbeef").toList() }

            assertEquals(TransactionResult.TimedOut, results.last())
            assertTrue(
                repository.callCount <= 5,
                "expected backoff to bound polls, but got ${repository.callCount}",
            )
        }

    @Test
    fun `invoke keeps the configured cadence on healthy pending polls without ballooning`() =
        runBlocking {
            // Regression for the review on issue #5363: exponential backoff must NOT apply to plain
            // Pending polls, or a normal multi-second confirmation on a fast chain would balloon to
            // the backoff cap. With a 1s interval over a ~4s window the loop should poll every
            // second (~4 polls); a ballooning backoff (1s, 2s, 4s) would fire only ~3.
            val repository = AlwaysPendingStatusRepository()
            val useCase =
                PollingTxStatusUseCaseImpl(
                    txStatusConfigurationProvider =
                        FakeTxStatusConfigurationProvider(
                            TxStatusConfiguration(pollIntervalSeconds = 1, maxWaitSeconds = 4)
                        ),
                    transactionStatusRepository = repository,
                    timeSource = TimeSource.Monotonic,
                )

            val results = withTimeout(20_000) { useCase(Chain.Solana, "deadbeef").toList() }

            assertEquals(TransactionResult.TimedOut, results.last())
            assertTrue(results.dropLast(1).all { it == TransactionResult.Pending })
            assertTrue(
                repository.callCount >= 4,
                "expected a steady ~1s cadence, but got only ${repository.callCount} polls",
            )
        }

    @Test
    fun `invoke times out promptly without oversleeping the backoff interval`() = runBlocking {
        // The poll interval (and thus the first backoff delay) is far larger than the timeout, so
        // an unclamped backoff would keep the loop sleeping long past the deadline. The delay must
        // be clamped to the remaining budget so TimedOut is emitted close to maxWaitSeconds.
        val repository = AlwaysPendingStatusRepository()
        val useCase =
            PollingTxStatusUseCaseImpl(
                txStatusConfigurationProvider =
                    FakeTxStatusConfigurationProvider(
                        TxStatusConfiguration(pollIntervalSeconds = 30, maxWaitSeconds = 2)
                    ),
                transactionStatusRepository = repository,
                timeSource = TimeSource.Monotonic,
            )

        val start = System.currentTimeMillis()
        val results = withTimeout(20_000) { useCase(Chain.Bittensor, "deadbeef").toList() }
        val elapsedMillis = System.currentTimeMillis() - start

        assertEquals(TransactionResult.TimedOut, results.last())
        assertTrue(
            elapsedMillis < 10_000,
            "expected timeout near maxWaitSeconds, but took ${elapsedMillis}ms",
        )
    }

    @Test
    fun `invoke stops polling after refunded result`() = runBlocking {
        val repository = AlwaysRefundedStatusRepository()
        val useCase =
            PollingTxStatusUseCaseImpl(
                txStatusConfigurationProvider =
                    FakeTxStatusConfigurationProvider(
                        TxStatusConfiguration(pollIntervalSeconds = 30, maxWaitSeconds = 60)
                    ),
                transactionStatusRepository = repository,
                timeSource = TimeSource.Monotonic,
            )

        val results = withTimeout(5_000) { useCase(Chain.ThorChain, "deadbeef").toList() }

        assertEquals(listOf(TransactionResult.Refunded("refunded")), results)
        assertEquals(1, repository.callCount)
    }

    @Test
    fun `invoke emits TimedOut once the deadline on the injected time source passes`() = runTest {
        val timeSource = TestTimeSource()
        val repository = TickingPendingStatusRepository(timeSource, pollInterval = 1.seconds)
        val useCase =
            PollingTxStatusUseCaseImpl(
                txStatusConfigurationProvider =
                    FakeTxStatusConfigurationProvider(
                        TxStatusConfiguration(pollIntervalSeconds = 1, maxWaitSeconds = 60)
                    ),
                transactionStatusRepository = repository,
                timeSource = timeSource,
            )

        val results = useCase(Chain.Ethereum, "deadbeef").toList()

        // Exactly maxWait / pollInterval checks fit before the deadline; the next loop iteration
        // sees it passed and emits TimedOut without another network call.
        assertEquals(60, repository.callCount)
        assertEquals(TransactionResult.TimedOut, results.last())
        assertTrue(results.dropLast(1).all { it == TransactionResult.Pending })
    }
}

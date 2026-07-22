package com.vultisig.wallet.data.usecases.txstatus

import com.vultisig.wallet.data.models.Chain
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Regression test for issue #5043's "joined device polls a never-committed tx forever" concern.
 * [PollingTxStatusUseCaseImpl] reads the wall clock directly (no injectable time source), so this
 * test drives real (short) delays via a tiny [TxStatusConfiguration] instead of `runTest` virtual
 * time — a `TestDispatcher`'s `delay()` skip wouldn't advance the `System.currentTimeMillis()` the
 * use case actually checks the elapsed time against.
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
            )

        // Bounds the test itself: if this ever regressed back to polling forever on NotFound, this
        // would hang and fail with a TimeoutCancellationException instead of hanging the suite.
        val results = withTimeout(10_000) { useCase(Chain.Qbtc, "deadbeef").toList() }

        assertEquals(TransactionResult.TimedOut, results.last())
        assertTrue(results.dropLast(1).all { it == TransactionResult.NotFound })
        assertTrue(repository.callCount >= 1)
    }

    @Test
    fun `invoke backs off instead of hammering the endpoint on sustained pending`() = runBlocking {
        // Regression for issue #5363: a rate-limited status endpoint is swallowed into Pending, so
        // the loop must grow its interval rather than re-hit at a fixed cadence. With a 1s base
        // interval the delays are 1s, 2s, 4s..., so over a ~7s window at most a handful of polls
        // fire — far fewer than the ~7 a fixed 1s interval would produce.
        val repository = AlwaysPendingStatusRepository()
        val useCase =
            PollingTxStatusUseCaseImpl(
                txStatusConfigurationProvider =
                    FakeTxStatusConfigurationProvider(
                        TxStatusConfiguration(pollIntervalSeconds = 1, maxWaitSeconds = 7)
                    ),
                transactionStatusRepository = repository,
            )

        val results = withTimeout(20_000) { useCase(Chain.Bittensor, "deadbeef").toList() }

        assertEquals(TransactionResult.TimedOut, results.last())
        assertTrue(results.dropLast(1).all { it == TransactionResult.Pending })
        assertTrue(
            repository.callCount <= 5,
            "expected backoff to bound polls, but got ${repository.callCount}",
        )
    }
}

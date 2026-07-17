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
}

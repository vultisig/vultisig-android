package com.vultisig.wallet.data.api.txstatus

import com.vultisig.wallet.data.api.PolkadotApi
import com.vultisig.wallet.data.db.models.TransactionHistoryEntity
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.repositories.TransactionHistoryRepository
import com.vultisig.wallet.data.usecases.txstatus.TransactionResult
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class PolkadotStatusProviderTest {

    private val polkadotApi = mockk<PolkadotApi>()
    private val transactionHistoryRepository = mockk<TransactionHistoryRepository>()
    private val provider = PolkadotStatusProvider(polkadotApi, transactionHistoryRepository)

    @Test
    fun `extrinsic found in a recent block maps to Confirmed`() = runTest {
        coEvery { transactionHistoryRepository.getTransaction(any(), any()) } returns null
        coEvery { polkadotApi.isExtrinsicInChain(TX_HASH, any()) } returns true

        val result = provider.checkStatus(TX_HASH, Chain.Polkadot)

        assertEquals(TransactionResult.Confirmed, result)
    }

    @Test
    fun `extrinsic not yet in a block keeps polling as Pending`() = runTest {
        coEvery { transactionHistoryRepository.getTransaction(any(), any()) } returns null
        coEvery { polkadotApi.isExtrinsicInChain(TX_HASH, any()) } returns false

        val result = provider.checkStatus(TX_HASH, Chain.Polkadot)

        assertEquals(TransactionResult.Pending, result)
    }

    @Test
    fun `transient RPC errors keep polling alive as Pending`() = runTest {
        coEvery { transactionHistoryRepository.getTransaction(any(), any()) } returns null
        coEvery { polkadotApi.isExtrinsicInChain(TX_HASH, any()) } throws
            RuntimeException("Connection timed out")

        val result = provider.checkStatus(TX_HASH, Chain.Polkadot)

        assertEquals(TransactionResult.Pending, result)
    }

    @Test
    fun `cancellation is not swallowed`() = runTest {
        coEvery { transactionHistoryRepository.getTransaction(any(), any()) } returns null
        coEvery { polkadotApi.isExtrinsicInChain(TX_HASH, any()) } throws
            CancellationException("cancelled")

        assertThrows<CancellationException> { provider.checkStatus(TX_HASH, Chain.Polkadot) }
    }

    @Test
    fun `an old broadcast scans deeper so a buried inclusion block is still reachable`() = runTest {
        // Broadcast 6 minutes ago: the inclusion block is far behind the live head now, so the
        // shallow head window the foreground poller used can no longer reach it.
        coEvery { transactionHistoryRepository.getTransaction(any(), any()) } returns
            entityBroadcastMinutesAgo(6)
        val depth = slot<Int>()
        coEvery { polkadotApi.isExtrinsicInChain(TX_HASH, capture(depth)) } returns true

        provider.checkStatus(TX_HASH, Chain.Polkadot)

        // ~60 blocks of elapsed time + margin, well past the 10-block foreground floor.
        assertTrue(depth.captured > 10, "expected a deep scan, got ${depth.captured}")
    }

    @Test
    fun `scan depth is capped so a stuck extrinsic never triggers an unbounded walk`() = runTest {
        coEvery { transactionHistoryRepository.getTransaction(any(), any()) } returns
            entityBroadcastMinutesAgo(600)
        val depth = slot<Int>()
        coEvery { polkadotApi.isExtrinsicInChain(TX_HASH, capture(depth)) } returns false

        provider.checkStatus(TX_HASH, Chain.Polkadot)

        assertEquals(MAX_SCAN_DEPTH, depth.captured)
    }

    private fun entityBroadcastMinutesAgo(minutes: Long): TransactionHistoryEntity =
        mockk(relaxed = true) {
            every { timestamp } returns System.currentTimeMillis() - minutes * 60 * 1_000
        }

    private companion object {
        const val TX_HASH = "0xdeadbeef"
        const val MAX_SCAN_DEPTH = 130
    }
}

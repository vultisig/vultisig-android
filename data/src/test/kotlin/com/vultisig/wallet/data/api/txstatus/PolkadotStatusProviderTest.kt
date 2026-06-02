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
import java.math.BigInteger
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

    @Test
    fun `extrinsic found inside the absolute inclusion window maps to Confirmed`() = runTest {
        coEvery { transactionHistoryRepository.getTransaction(any(), any()) } returns
            entityWithBroadcastBlock(BROADCAST_BLOCK)
        coEvery { polkadotApi.getBlockHeader() } returns BigInteger.valueOf(BROADCAST_BLOCK + 5)
        val from = slot<Long>()
        val to = slot<Long>()
        coEvery { polkadotApi.isExtrinsicInBlockRange(TX_HASH, capture(from), capture(to)) } returns
            true

        val result = provider.checkStatus(TX_HASH, Chain.Polkadot)

        assertEquals(TransactionResult.Confirmed, result)
        // Anchored at the broadcast block; the window top is capped at the live head (+5 here).
        assertEquals(BROADCAST_BLOCK, from.captured)
        assertEquals(BROADCAST_BLOCK + 5, to.captured)
    }

    @Test
    fun `not yet included while the mortal era is still open stays Pending`() = runTest {
        coEvery { transactionHistoryRepository.getTransaction(any(), any()) } returns
            entityWithBroadcastBlock(BROADCAST_BLOCK)
        // Head is past the inclusion window but still inside the finality margin.
        coEvery { polkadotApi.getBlockHeader() } returns
            BigInteger.valueOf(BROADCAST_BLOCK + INCLUSION_WINDOW_BLOCKS + 5)
        coEvery { polkadotApi.isExtrinsicInBlockRange(TX_HASH, any(), any()) } returns false

        val result = provider.checkStatus(TX_HASH, Chain.Polkadot)

        assertEquals(TransactionResult.Pending, result)
    }

    @Test
    fun `missing extrinsic past the mortal era resolves to terminal Failed`() = runTest {
        coEvery { transactionHistoryRepository.getTransaction(any(), any()) } returns
            entityWithBroadcastBlock(BROADCAST_BLOCK)
        // Head has advanced well past the window + finality margin, so it can never be included.
        coEvery { polkadotApi.getBlockHeader() } returns
            BigInteger.valueOf(BROADCAST_BLOCK + INCLUSION_WINDOW_BLOCKS + FINALITY_MARGIN + 1)
        coEvery { polkadotApi.isExtrinsicInBlockRange(TX_HASH, any(), any()) } returns false

        val result = provider.checkStatus(TX_HASH, Chain.Polkadot)

        assertTrue(result is TransactionResult.Failed, "expected terminal Failed, got $result")
    }

    private fun entityBroadcastMinutesAgo(minutes: Long): TransactionHistoryEntity =
        mockk(relaxed = true) {
            every { timestamp } returns System.currentTimeMillis() - minutes * 60 * 1_000
            every { broadcastBlockNumber } returns null
        }

    private fun entityWithBroadcastBlock(block: Long): TransactionHistoryEntity =
        mockk(relaxed = true) { every { broadcastBlockNumber } returns block }

    private companion object {
        const val TX_HASH = "0xdeadbeef"
        const val MAX_SCAN_DEPTH = 130
        const val BROADCAST_BLOCK = 1_000_000L
        const val INCLUSION_WINDOW_BLOCKS = 64L
        const val FINALITY_MARGIN = 10L
    }
}

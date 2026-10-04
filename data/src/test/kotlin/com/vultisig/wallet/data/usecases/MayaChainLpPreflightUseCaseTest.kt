package com.vultisig.wallet.data.usecases

import com.vultisig.wallet.data.api.MayaChainApi
import com.vultisig.wallet.data.api.MayaNodePool
import com.vultisig.wallet.data.api.models.MayaLatestBlockInfoResponse
import com.vultisig.wallet.data.api.models.thorchain.THORChainInboundAddress
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

internal class MayaChainLpPreflightUseCaseTest {

    private lateinit var api: MayaChainApi
    private lateinit var useCase: MayaChainLpPreflightUseCaseImpl

    @BeforeEach
    fun setUp() {
        api = mockk()
        useCase = MayaChainLpPreflightUseCaseImpl(api)

        // Default: the live mainnet shape — every pause key at 0, BTC inbound open, pool Available.
        coEvery { api.getMayaConstants() } returns mapOf("PAUSELP" to 0L, "PAUSELPBTC" to 0L)
        coEvery { api.getLatestBlock() } returns latestBlock(HEIGHT)
        coEvery { api.getInboundAddresses() } returns listOf(inbound("BTC"))
        coEvery { api.getPool(POOL) } returns pool(POOL, "Available")
    }

    @Test fun `clean state passes`() = runTest { assertNull(useCase(POOL, isPairedAdd = true)) }

    @Test
    fun `global PAUSELP past its activation height blocks`() = runTest {
        coEvery { api.getMayaConstants() } returns mapOf("PAUSELP" to HEIGHT - 1)

        assertEquals(MayaChainLpPreflightBlock.LpPaused(POOL), useCase(POOL, isPairedAdd = true))
    }

    @Test
    fun `per-chain PAUSELP key blocks only its own chain`() = runTest {
        coEvery { api.getMayaConstants() } returns mapOf("PAUSELPBTC" to 1L, "PAUSELPETH" to 0L)

        assertEquals(MayaChainLpPreflightBlock.LpPaused(POOL), useCase(POOL, isPairedAdd = true))
    }

    @Test
    fun `a pause scheduled for a future height does not block yet`() = runTest {
        coEvery { api.getMayaConstants() } returns mapOf("PAUSELP" to HEIGHT + 100)

        assertNull(useCase(POOL, isPairedAdd = true))
    }

    @Test
    fun `a set pause key still blocks when the block height cannot be read`() = runTest {
        coEvery { api.getMayaConstants() } returns mapOf("PAUSELPBTC" to HEIGHT + 100)
        coEvery { api.getLatestBlock() } throws RuntimeException("timeout")

        assertEquals(MayaChainLpPreflightBlock.LpPaused(POOL), useCase(POOL, isPairedAdd = true))
    }

    @Test
    fun `halted asset-chain inbound blocks`() = runTest {
        coEvery { api.getInboundAddresses() } returns listOf(inbound("BTC", halted = true))

        assertEquals(
            MayaChainLpPreflightBlock.ChainHalted("BTC"),
            useCase(POOL, isPairedAdd = true),
        )
    }

    @Test
    fun `inbound reporting LP actions paused blocks`() = runTest {
        coEvery { api.getInboundAddresses() } returns listOf(inbound("BTC", lpPaused = true))

        assertEquals(
            MayaChainLpPreflightBlock.ChainHalted("BTC"),
            useCase(POOL, isPairedAdd = true),
        )
    }

    @Test
    fun `a MAYA pool never reads the inbound set`() = runTest {
        coEvery { api.getPool("MAYA.MAYA") } returns pool("MAYA.MAYA", "Available")

        assertNull(useCase("MAYA.MAYA", isPairedAdd = false))
        coVerify(exactly = 0) { api.getInboundAddresses() }
    }

    @Test
    fun `staged pool takes a paired add`() = runTest {
        coEvery { api.getPool(POOL) } returns pool(POOL, "Staged")

        assertNull(useCase(POOL, isPairedAdd = true))
    }

    @Test
    fun `staged pool refuses an unpaired add`() = runTest {
        coEvery { api.getPool(POOL) } returns pool(POOL, "Staged")

        assertEquals(
            MayaChainLpPreflightBlock.StagedPoolRequiresPairedAdd(POOL),
            useCase(POOL, isPairedAdd = false),
        )
    }

    @Test
    fun `suspended pool is not available`() = runTest {
        coEvery { api.getPool(POOL) } returns pool(POOL, "Suspended")

        assertEquals(
            MayaChainLpPreflightBlock.PoolNotAvailable(POOL, "Suspended"),
            useCase(POOL, isPairedAdd = true),
        )
    }

    @Test
    fun `failed signals fail open`() = runTest {
        coEvery { api.getMayaConstants() } throws RuntimeException("mimir down")
        coEvery { api.getInboundAddresses() } throws RuntimeException("inbound down")
        coEvery { api.getPool(POOL) } throws RuntimeException("404")

        assertNull(useCase(POOL, isPairedAdd = false))
    }

    @Test
    fun `cancellation propagates`() = runTest {
        coEvery { api.getPool(POOL) } throws CancellationException("cancelled")

        assertFailsWith<CancellationException> { useCase(POOL, isPairedAdd = true) }
    }

    private fun latestBlock(height: Long): MayaLatestBlockInfoResponse =
        mockk { every { block.header.height } returns height.toString() }

    private fun inbound(
        chain: String,
        halted: Boolean = false,
        lpPaused: Boolean = false,
    ): THORChainInboundAddress =
        THORChainInboundAddress(
            chain = chain,
            address = "bc1qinbound",
            halted = halted,
            chainLPActionsPaused = lpPaused,
        )

    private fun pool(asset: String, status: String) = MayaNodePool(asset = asset, status = status)

    private companion object {
        const val POOL = "BTC.BTC"
        const val HEIGHT = 15_000_000L
    }
}

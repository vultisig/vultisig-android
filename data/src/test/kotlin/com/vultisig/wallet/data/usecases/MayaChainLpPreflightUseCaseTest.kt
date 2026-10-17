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
    fun `a trading halt on the pool itself blocks`() = runTest {
        coEvery { api.getMayaConstants() } returns
            mapOf("HALTTRADING-BTC-BTC" to HEIGHT - 1, "HALTTRADING-ETH-ETH" to 0L)

        assertEquals(MayaChainLpPreflightBlock.LpPaused(POOL), useCase(POOL, isPairedAdd = true))
    }

    @Test
    fun `a trading halt on another pool does not block`() = runTest {
        coEvery { api.getMayaConstants() } returns mapOf("HALTTRADING-ETH-ETH" to HEIGHT - 1)

        assertNull(useCase(POOL, isPairedAdd = true))
    }

    @Test
    fun `a token pool's trading halt key keeps its contract suffix`() = runTest {
        val tokenPool = "ETH.USDC-0xA0B86991C6218B36C1D19D4A2E9EB0CE3606EB48"
        coEvery { api.getPool(tokenPool) } returns pool(tokenPool, "Available")
        coEvery { api.getInboundAddresses() } returns listOf(inbound("ETH"))
        coEvery { api.getMayaConstants() } returns
            mapOf("HALTTRADING-ETH-USDC-0XA0B86991C6218B36C1D19D4A2E9EB0CE3606EB48" to 1L)

        assertEquals(
            MayaChainLpPreflightBlock.LpPaused(tokenPool),
            useCase(tokenPool, isPairedAdd = true),
        )
    }

    @Test
    fun `chain and global trading halts block`() = runTest {
        coEvery { api.getMayaConstants() } returns mapOf("HALTBTCTRADING" to 1L)
        assertEquals(MayaChainLpPreflightBlock.LpPaused(POOL), useCase(POOL, isPairedAdd = true))

        coEvery { api.getMayaConstants() } returns mapOf("HALTTRADING" to 1L)
        assertEquals(MayaChainLpPreflightBlock.LpPaused(POOL), useCase(POOL, isPairedAdd = true))
    }

    @Test
    fun `inbound reporting trading paused blocks`() = runTest {
        coEvery { api.getInboundAddresses() } returns
            listOf(inbound("BTC").copy(chainTradingPaused = true))
        assertEquals(
            MayaChainLpPreflightBlock.ChainHalted("BTC"),
            useCase(POOL, isPairedAdd = true),
        )

        coEvery { api.getInboundAddresses() } returns
            listOf(inbound("BTC").copy(globalTradingPaused = true))
        assertEquals(
            MayaChainLpPreflightBlock.ChainHalted("BTC"),
            useCase(POOL, isPairedAdd = true),
        )
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

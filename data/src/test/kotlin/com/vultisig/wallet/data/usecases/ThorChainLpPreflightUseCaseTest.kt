package com.vultisig.wallet.data.usecases

import com.vultisig.wallet.data.api.ThorChainApi
import com.vultisig.wallet.data.api.models.thorchain.THORChainInboundAddress
import com.vultisig.wallet.data.api.models.thorchain.ThorChainPoolJson
import com.vultisig.wallet.data.repositories.ThorMimirRepository
import io.mockk.coEvery
import io.mockk.mockk
import java.math.BigInteger
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

internal class ThorChainLpPreflightUseCaseTest {

    private lateinit var api: ThorChainApi
    private lateinit var mimir: ThorMimirRepository
    private lateinit var useCase: ThorChainLpPreflightUseCaseImpl

    @BeforeEach
    fun setUp() {
        api = mockk()
        mimir = mockk()
        useCase = ThorChainLpPreflightUseCaseImpl(api, mimir)

        // Default: clean state — every signal off, pool Available, no inbound flags.
        coEvery { mimir.isLpPaused(any()) } returns false
        coEvery { mimir.isLpHalted(any()) } returns false
        coEvery { api.getPool(any()) } returns availablePool(POOL_USDT)
        coEvery { api.getTHORChainInboundAddresses() } returns
            listOf(inbound(chain = "ETH", halted = false))
    }

    @Test fun `clean state passes`() = runTest { assertNull(useCase(POOL_USDT)) }

    @Test
    fun `mimir LP pause is reported first`() = runTest {
        coEvery { mimir.isLpPaused(POOL_USDT) } returns true

        val block = useCase(POOL_USDT)

        assertTrue(block is ThorChainLpPreflightBlock.LpPaused)
        assertEquals(POOL_USDT, (block as ThorChainLpPreflightBlock.LpPaused).pool)
    }

    @Test
    fun `chain LP halt is reported when mimir LP is not paused`() = runTest {
        coEvery { mimir.isLpHalted("ETH") } returns true

        val block = useCase(POOL_USDT)

        assertTrue(block is ThorChainLpPreflightBlock.ChainLpHalted)
        assertEquals("ETH", (block as ThorChainLpPreflightBlock.ChainLpHalted).chainPrefix)
    }

    @Test
    fun `pool status not Available is reported`() = runTest {
        coEvery { api.getPool(POOL_USDT) } returns
            ThorChainPoolJson(
                asset = "ETH.USDT-0xdac",
                assetTorPrice = BigInteger.ZERO,
                status = "Staged",
            )

        val block = useCase(POOL_USDT)

        assertTrue(block is ThorChainLpPreflightBlock.PoolNotAvailable)
        assertEquals("Staged", (block as ThorChainLpPreflightBlock.PoolNotAvailable).status)
    }

    @Test
    fun `inbound chain_lp_actions_paused is reported`() = runTest {
        coEvery { api.getTHORChainInboundAddresses() } returns
            listOf(inbound(chain = "ETH", chainLpActionsPaused = true))

        val block = useCase(POOL_USDT)

        assertTrue(block is ThorChainLpPreflightBlock.InboundLpPaused)
    }

    @Test
    fun `inbound halted is reported`() = runTest {
        coEvery { api.getTHORChainInboundAddresses() } returns
            listOf(inbound(chain = "ETH", halted = true))

        val block = useCase(POOL_USDT)

        assertTrue(block is ThorChainLpPreflightBlock.InboundLpPaused)
    }

    @Test
    fun `inbound global trading paused is reported`() = runTest {
        coEvery { api.getTHORChainInboundAddresses() } returns
            listOf(inbound(chain = "ETH", globalTradingPaused = true))

        val block = useCase(POOL_USDT)

        assertTrue(block is ThorChainLpPreflightBlock.InboundLpPaused)
    }

    @Test
    fun `inbound entry for unrelated chain is ignored`() = runTest {
        coEvery { api.getTHORChainInboundAddresses() } returns
            listOf(inbound(chain = "BTC", halted = true), inbound(chain = "ETH", halted = false))

        assertNull(useCase(POOL_USDT))
    }

    @Test
    fun `network errors are swallowed - clean state still passes`() = runTest {
        coEvery { mimir.isLpPaused(any()) } throws RuntimeException("thornode down")
        coEvery { mimir.isLpHalted(any()) } throws RuntimeException("thornode down")
        coEvery { api.getPool(any()) } throws RuntimeException("thornode down")
        coEvery { api.getTHORChainInboundAddresses() } throws RuntimeException("thornode down")

        assertNull(useCase(POOL_USDT))
    }

    @Test
    fun `pool status missing is treated as Available`() = runTest {
        coEvery { api.getPool(POOL_USDT) } returns
            ThorChainPoolJson(asset = POOL_USDT, assetTorPrice = BigInteger.ZERO, status = null)

        assertNull(useCase(POOL_USDT))
    }

    @Test
    fun `pool status Available is not flagged regardless of case`() = runTest {
        coEvery { api.getPool(POOL_USDT) } returns
            ThorChainPoolJson(
                asset = POOL_USDT,
                assetTorPrice = BigInteger.ZERO,
                status = "available",
            )

        assertNull(useCase(POOL_USDT))
    }

    private fun availablePool(asset: String): ThorChainPoolJson =
        ThorChainPoolJson(asset = asset, assetTorPrice = BigInteger.ZERO, status = "Available")

    private fun inbound(
        chain: String,
        halted: Boolean = false,
        chainLpActionsPaused: Boolean = false,
        globalTradingPaused: Boolean = false,
    ): THORChainInboundAddress =
        THORChainInboundAddress(
            chain = chain,
            address = "0xinbound",
            halted = halted,
            globalTradingPaused = globalTradingPaused,
            chainTradingPaused = false,
            chainLPActionsPaused = chainLpActionsPaused,
            gasRate = "0",
            gasRateUnits = "gwei",
        )

    private companion object {
        const val POOL_USDT = "ETH.USDT-0xdac17f958d2ee523a2206206994597c13d831ec7"
    }
}

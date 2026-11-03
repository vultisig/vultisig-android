package com.vultisig.wallet.data.usecases

import com.vultisig.wallet.data.api.ThorChainApi
import com.vultisig.wallet.data.api.models.thorchain.THORChainInboundAddress
import com.vultisig.wallet.data.api.models.thorchain.ThorChainPoolJson
import com.vultisig.wallet.data.repositories.ThorMimirRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import java.math.BigInteger
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
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

        // Default: clean state — every signal off, pool Available.
        coEvery { mimir.isLpPaused(any()) } returns false
        coEvery { mimir.isLpHalted(any()) } returns false
        coEvery { api.getPool(any()) } returns availablePool(POOL_USDT)
        coEvery { api.getTHORChainInboundAddresses() } returns
            listOf(inbound("ETH"), inbound("SOL"))
    }

    @Test
    fun `clean state passes`() = runTest {
        assertNull(useCase(POOL_USDT, isPairedAdd = false, checkAssetChainInbound = false))
    }

    @Test
    fun `mimir LP pause is reported first`() = runTest {
        coEvery { mimir.isLpPaused(POOL_USDT) } returns true

        val block = useCase(POOL_USDT, isPairedAdd = false, checkAssetChainInbound = false)

        assertTrue(block is ThorChainLpPreflightBlock.LpPaused)
        assertEquals(POOL_USDT, (block as ThorChainLpPreflightBlock.LpPaused).pool)
    }

    @Test
    fun `chain LP halt is reported when mimir LP is not paused`() = runTest {
        coEvery { mimir.isLpHalted("ETH") } returns true

        val block = useCase(POOL_USDT, isPairedAdd = false, checkAssetChainInbound = false)

        assertTrue(block is ThorChainLpPreflightBlock.ChainLpHalted)
        assertEquals("ETH", (block as ThorChainLpPreflightBlock.ChainLpHalted).chainPrefix)
    }

    @Test
    fun `clean state passes a paired add too`() = runTest {
        assertNull(useCase(POOL_USDT, isPairedAdd = true, checkAssetChainInbound = false))
    }

    @Test
    fun `suspended pool is reported even for a paired add`() = runTest {
        coEvery { api.getPool(POOL_USDT) } returns poolWithStatus("Suspended")

        val block = useCase(POOL_USDT, isPairedAdd = true, checkAssetChainInbound = false)

        assertTrue(block is ThorChainLpPreflightBlock.PoolNotAvailable)
        assertEquals("Suspended", (block as ThorChainLpPreflightBlock.PoolNotAvailable).status)
    }

    @Test
    fun `staged pool passes a paired add`() = runTest {
        coEvery { api.getPool(POOL_USDT) } returns poolWithStatus("Staged")

        assertNull(useCase(POOL_USDT, isPairedAdd = true, checkAssetChainInbound = false))
    }

    @Test
    fun `staged pool blocks an unpaired add with its own reason`() = runTest {
        coEvery { api.getPool(POOL_USDT) } returns poolWithStatus("Staged")

        val block = useCase(POOL_USDT, isPairedAdd = false, checkAssetChainInbound = false)

        assertTrue(block is ThorChainLpPreflightBlock.StagedPoolRequiresPairedAdd)
        assertEquals(
            POOL_USDT,
            (block as ThorChainLpPreflightBlock.StagedPoolRequiresPairedAdd).pool,
        )
    }

    @Test
    fun `staged status is matched regardless of case`() = runTest {
        coEvery { api.getPool(POOL_USDT) } returns poolWithStatus("staged")

        assertNull(useCase(POOL_USDT, isPairedAdd = true, checkAssetChainInbound = false))
    }

    @Test
    fun `LP pause still blocks a paired add to a staged pool`() = runTest {
        coEvery { mimir.isLpPaused(POOL_USDT) } returns true
        coEvery { api.getPool(POOL_USDT) } returns poolWithStatus("Staged")

        assertTrue(
            useCase(POOL_USDT, isPairedAdd = true, checkAssetChainInbound = false)
                is ThorChainLpPreflightBlock.LpPaused
        )
    }

    @Test
    fun `chain LP halt still blocks a paired add to a staged pool`() = runTest {
        coEvery { mimir.isLpHalted("ETH") } returns true
        coEvery { api.getPool(POOL_USDT) } returns poolWithStatus("Staged")

        assertTrue(
            useCase(POOL_USDT, isPairedAdd = true, checkAssetChainInbound = false)
                is ThorChainLpPreflightBlock.ChainLpHalted
        )
    }

    @Test
    fun `when multiple signals block, mimir LP pause wins over chain halt and pool status`() =
        runTest {
            coEvery { mimir.isLpPaused(POOL_USDT) } returns true
            coEvery { mimir.isLpHalted("ETH") } returns true
            coEvery { api.getPool(POOL_USDT) } returns
                ThorChainPoolJson(
                    asset = POOL_USDT,
                    assetTorPrice = BigInteger.ZERO,
                    status = "Staged",
                )

            assertTrue(
                useCase(POOL_USDT, isPairedAdd = false, checkAssetChainInbound = false)
                    is ThorChainLpPreflightBlock.LpPaused
            )
        }

    @Test
    fun `when chain halt and pool status both block, chain halt wins`() = runTest {
        coEvery { mimir.isLpHalted("ETH") } returns true
        coEvery { api.getPool(POOL_USDT) } returns
            ThorChainPoolJson(asset = POOL_USDT, assetTorPrice = BigInteger.ZERO, status = "Staged")

        assertTrue(
            useCase(POOL_USDT, isPairedAdd = false, checkAssetChainInbound = false)
                is ThorChainLpPreflightBlock.ChainLpHalted
        )
    }

    @Test
    fun `network errors are swallowed - clean state still passes`() = runTest {
        coEvery { mimir.isLpPaused(any()) } throws RuntimeException("thornode down")
        coEvery { mimir.isLpHalted(any()) } throws RuntimeException("thornode down")
        coEvery { api.getPool(any()) } throws RuntimeException("thornode down")

        assertNull(useCase(POOL_USDT, isPairedAdd = false, checkAssetChainInbound = false))
    }

    @Test
    fun `CancellationException is propagated, not swallowed by the fail-open probe`() = runTest {
        coEvery { mimir.isLpPaused(any()) } throws CancellationException("parent cancelled")

        assertFailsWith<CancellationException> {
            useCase(POOL_USDT, isPairedAdd = false, checkAssetChainInbound = false)
        }
    }

    @Test
    fun `pool status missing is treated as Available`() = runTest {
        coEvery { api.getPool(POOL_USDT) } returns
            ThorChainPoolJson(asset = POOL_USDT, assetTorPrice = BigInteger.ZERO, status = null)

        assertNull(useCase(POOL_USDT, isPairedAdd = false, checkAssetChainInbound = false))
    }

    @Test
    fun `pool status Available is not flagged regardless of case`() = runTest {
        coEvery { api.getPool(POOL_USDT) } returns
            ThorChainPoolJson(
                asset = POOL_USDT,
                assetTorPrice = BigInteger.ZERO,
                status = "available",
            )

        assertNull(useCase(POOL_USDT, isPairedAdd = false, checkAssetChainInbound = false))
    }

    @Test
    fun `RUNE-side clean state passes with all inbound flags off`() = runTest {
        assertNull(useCase(POOL_USDT, isPairedAdd = true, checkAssetChainInbound = true))
    }

    @Test
    fun `RUNE-side add blocks on asset chain halted inbound flag`() = runTest {
        givenEthInbound(inbound("ETH", halted = true))

        assertChainHalted(
            "ETH",
            useCase(POOL_USDT, isPairedAdd = true, checkAssetChainInbound = true),
        )
    }

    @Test
    fun `RUNE-side add blocks on global trading paused inbound flag`() = runTest {
        givenEthInbound(inbound("ETH", globalTradingPaused = true))

        assertChainHalted(
            "ETH",
            useCase(POOL_USDT, isPairedAdd = true, checkAssetChainInbound = true),
        )
    }

    @Test
    fun `RUNE-side add blocks on chain trading paused inbound flag`() = runTest {
        givenEthInbound(inbound("ETH", chainTradingPaused = true))

        assertChainHalted(
            "ETH",
            useCase(POOL_USDT, isPairedAdd = true, checkAssetChainInbound = true),
        )
    }

    @Test
    fun `RUNE-side add blocks on chain LP actions paused inbound flag`() = runTest {
        givenEthInbound(inbound("ETH", chainLpActionsPaused = true))

        assertChainHalted(
            "ETH",
            useCase(POOL_USDT, isPairedAdd = true, checkAssetChainInbound = true),
        )
    }

    @Test
    fun `live SOL shape - trading halted with HALTSOLCHAIN off - blocks the RUNE-side add`() =
        runTest {
            coEvery { api.getPool(POOL_SOL) } returns availablePool(POOL_SOL)
            coEvery { api.getTHORChainInboundAddresses() } returns
                listOf(
                    inbound("ETH"),
                    inbound("SOL", halted = true, chainTradingPaused = true),
                )

            assertChainHalted(
                "SOL",
                useCase(POOL_SOL, isPairedAdd = true, checkAssetChainInbound = true),
            )
        }

    @Test
    fun `inbound chain is matched regardless of case`() = runTest {
        givenEthInbound(inbound("eth", chainTradingPaused = true))

        assertChainHalted(
            "ETH",
            useCase(POOL_USDT, isPairedAdd = true, checkAssetChainInbound = true),
        )
    }

    @Test
    fun `asset-side preflight never reads inbound addresses`() = runTest {
        givenEthInbound(inbound("ETH", chainTradingPaused = true))

        assertNull(useCase(POOL_USDT, isPairedAdd = true, checkAssetChainInbound = false))
        coVerify(exactly = 0) { api.getTHORChainInboundAddresses() }
    }

    @Test
    fun `inbound fetch failure does not block the RUNE-side add`() = runTest {
        coEvery { api.getTHORChainInboundAddresses() } throws RuntimeException("thornode down")

        assertNull(useCase(POOL_USDT, isPairedAdd = true, checkAssetChainInbound = true))
    }

    @Test
    fun `CancellationException from the inbound fetch is propagated`() = runTest {
        coEvery { api.getTHORChainInboundAddresses() } throws CancellationException("cancelled")

        assertFailsWith<CancellationException> {
            useCase(POOL_USDT, isPairedAdd = true, checkAssetChainInbound = true)
        }
    }

    @Test
    fun `chain missing from the inbound list does not block`() = runTest {
        coEvery { api.getTHORChainInboundAddresses() } returns
            listOf(inbound("SOL", halted = true, chainTradingPaused = true))

        assertNull(useCase(POOL_USDT, isPairedAdd = true, checkAssetChainInbound = true))
    }

    @Test
    fun `THOR pool skips the inbound probe`() = runTest {
        coEvery { api.getPool(POOL_TCY) } returns availablePool(POOL_TCY)

        assertNull(useCase(POOL_TCY, isPairedAdd = false, checkAssetChainInbound = true))
        coVerify(exactly = 0) { api.getTHORChainInboundAddresses() }
    }

    @Test
    fun `mimir LP pause still wins over an inbound halt`() = runTest {
        coEvery { mimir.isLpPaused(POOL_USDT) } returns true
        givenEthInbound(inbound("ETH", chainTradingPaused = true))

        assertTrue(
            useCase(POOL_USDT, isPairedAdd = true, checkAssetChainInbound = true)
                is ThorChainLpPreflightBlock.LpPaused
        )
    }

    @Test
    fun `inbound halt wins over a non-Available pool status`() = runTest {
        coEvery { api.getPool(POOL_USDT) } returns poolWithStatus("Suspended")
        givenEthInbound(inbound("ETH", chainTradingPaused = true))

        assertChainHalted(
            "ETH",
            useCase(POOL_USDT, isPairedAdd = true, checkAssetChainInbound = true),
        )
    }

    private fun givenEthInbound(eth: THORChainInboundAddress) {
        coEvery { api.getTHORChainInboundAddresses() } returns listOf(eth, inbound("SOL"))
    }

    private fun assertChainHalted(chainPrefix: String, block: ThorChainLpPreflightBlock?) {
        assertTrue(block is ThorChainLpPreflightBlock.ChainLpHalted)
        assertEquals(chainPrefix, block.chainPrefix)
    }

    private fun inbound(
        chain: String,
        halted: Boolean = false,
        globalTradingPaused: Boolean = false,
        chainTradingPaused: Boolean = false,
        chainLpActionsPaused: Boolean = false,
    ) =
        THORChainInboundAddress(
            chain = chain,
            address = "inbound-$chain",
            halted = halted,
            globalTradingPaused = globalTradingPaused,
            chainTradingPaused = chainTradingPaused,
            chainLPActionsPaused = chainLpActionsPaused,
        )

    private fun poolWithStatus(status: String): ThorChainPoolJson =
        ThorChainPoolJson(asset = POOL_USDT, assetTorPrice = BigInteger.ZERO, status = status)

    private fun availablePool(asset: String): ThorChainPoolJson =
        ThorChainPoolJson(asset = asset, assetTorPrice = BigInteger.ZERO, status = "Available")

    private companion object {
        const val POOL_USDT = "ETH.USDT-0xdac17f958d2ee523a2206206994597c13d831ec7"
        const val POOL_SOL = "SOL.SOL"
        const val POOL_TCY = "THOR.TCY"
    }
}

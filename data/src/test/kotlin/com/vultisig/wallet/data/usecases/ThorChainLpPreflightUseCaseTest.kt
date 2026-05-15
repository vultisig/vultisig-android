package com.vultisig.wallet.data.usecases

import com.vultisig.wallet.data.api.ThorChainApi
import com.vultisig.wallet.data.api.models.thorchain.ThorChainPoolJson
import com.vultisig.wallet.data.repositories.ThorMimirRepository
import io.mockk.coEvery
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

            assertTrue(useCase(POOL_USDT) is ThorChainLpPreflightBlock.LpPaused)
        }

    @Test
    fun `when chain halt and pool status both block, chain halt wins`() = runTest {
        coEvery { mimir.isLpHalted("ETH") } returns true
        coEvery { api.getPool(POOL_USDT) } returns
            ThorChainPoolJson(asset = POOL_USDT, assetTorPrice = BigInteger.ZERO, status = "Staged")

        assertTrue(useCase(POOL_USDT) is ThorChainLpPreflightBlock.ChainLpHalted)
    }

    @Test
    fun `network errors are swallowed - clean state still passes`() = runTest {
        coEvery { mimir.isLpPaused(any()) } throws RuntimeException("thornode down")
        coEvery { mimir.isLpHalted(any()) } throws RuntimeException("thornode down")
        coEvery { api.getPool(any()) } throws RuntimeException("thornode down")

        assertNull(useCase(POOL_USDT))
    }

    @Test
    fun `CancellationException is propagated, not swallowed by the fail-open probe`() = runTest {
        coEvery { mimir.isLpPaused(any()) } throws CancellationException("parent cancelled")

        assertFailsWith<CancellationException> { useCase(POOL_USDT) }
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

    private companion object {
        const val POOL_USDT = "ETH.USDT-0xdac17f958d2ee523a2206206994597c13d831ec7"
    }
}

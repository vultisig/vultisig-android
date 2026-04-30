package com.vultisig.wallet.data.usecases

import com.vultisig.wallet.data.api.ThorChainApi
import com.vultisig.wallet.data.api.models.thorchain.ThorChainLiquidityProviderJson
import com.vultisig.wallet.data.api.models.thorchain.ThorChainPoolStatsJson
import com.vultisig.wallet.data.utils.NetworkException
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import java.math.BigInteger
import kotlin.test.assertFailsWith
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

internal class GetThorChainLpPositionsUseCaseTest {

    private lateinit var api: ThorChainApi
    private lateinit var useCase: GetThorChainLpPositionsUseCaseImpl

    @BeforeEach
    fun setUp() {
        api = mockk()
        useCase = GetThorChainLpPositionsUseCaseImpl(api)
    }

    @Test
    fun `returns positions only for pools where the user has units`() = runTest {
        coEvery { api.getPoolStats(any()) } returns
            listOf(
                pool("BTC.BTC", apr = "0.0500"),
                pool("ETH.ETH", apr = "0.1000"),
                pool("LTC.LTC", apr = "0.0700"),
            )
        coEvery { api.getLiquidityProvider("BTC.BTC", RUNE_ADDR) } returns
            lp(units = "1000", runeRedeem = "1500", assetRedeem = "2500")
        coEvery { api.getLiquidityProvider("ETH.ETH", RUNE_ADDR) } returns null
        coEvery { api.getLiquidityProvider("LTC.LTC", RUNE_ADDR) } returns lp(units = "0")

        val positions = useCase(runeAddress = RUNE_ADDR)

        assertEquals(1, positions.size)
        val btc = positions.single()
        assertEquals("BTC.BTC", btc.pool)
        assertEquals(BigInteger("1000"), btc.units)
        assertEquals(BigInteger("1500"), btc.runeRedeemValue)
        assertEquals(BigInteger("2500"), btc.assetRedeemValue)
        assertEquals(0.05, btc.annualPercentageRate)
    }

    @Test
    fun `falls back to per-pool asset address when runeAddress has no position`() = runTest {
        coEvery { api.getPoolStats(any()) } returns
            listOf(pool("BTC.BTC"), pool("ETH.ETH"), pool("LTC.LTC"))
        coEvery { api.getLiquidityProvider("BTC.BTC", RUNE_ADDR) } returns null
        coEvery { api.getLiquidityProvider("BTC.BTC", BTC_ADDR) } returns lp(units = "42")
        coEvery { api.getLiquidityProvider("ETH.ETH", RUNE_ADDR) } returns null
        coEvery { api.getLiquidityProvider("ETH.ETH", ETH_ADDR) } returns lp(units = "7")
        coEvery { api.getLiquidityProvider("LTC.LTC", RUNE_ADDR) } returns null

        val positions =
            useCase(
                runeAddress = RUNE_ADDR,
                assetAddressesByPool = mapOf("BTC.BTC" to BTC_ADDR, "ETH.ETH" to ETH_ADDR),
            )

        assertEquals(setOf("BTC.BTC", "ETH.ETH"), positions.map { it.pool }.toSet())
        coVerify { api.getLiquidityProvider("BTC.BTC", BTC_ADDR) }
        coVerify { api.getLiquidityProvider("ETH.ETH", ETH_ADDR) }
        // LTC has no fallback in the map; ensure we don't accidentally try BTC_ADDR for LTC.
        coVerify(exactly = 0) { api.getLiquidityProvider("LTC.LTC", BTC_ADDR) }
        coVerify(exactly = 0) { api.getLiquidityProvider("LTC.LTC", ETH_ADDR) }
    }

    @Test
    fun `skips pools that are not available`() = runTest {
        coEvery { api.getPoolStats(any()) } returns
            listOf(pool("BTC.BTC", status = "staged"), pool("ETH.ETH"))
        coEvery { api.getLiquidityProvider("ETH.ETH", RUNE_ADDR) } returns lp(units = "1")

        val positions = useCase(runeAddress = RUNE_ADDR)

        assertEquals(listOf("ETH.ETH"), positions.map { it.pool })
        coVerify(exactly = 0) { api.getLiquidityProvider("BTC.BTC", any()) }
    }

    @Test
    fun `swallows per-pool network errors and continues`() = runTest {
        coEvery { api.getPoolStats(any()) } returns listOf(pool("BTC.BTC"), pool("ETH.ETH"))
        coEvery { api.getLiquidityProvider("BTC.BTC", RUNE_ADDR) } throws
            NetworkException(httpStatusCode = 500, message = "boom")
        coEvery { api.getLiquidityProvider("ETH.ETH", RUNE_ADDR) } returns lp(units = "5")

        val positions = useCase(runeAddress = RUNE_ADDR)

        assertEquals(listOf("ETH.ETH"), positions.map { it.pool })
    }

    @Test
    fun `propagates unexpected per-pool exceptions`() = runTest {
        coEvery { api.getPoolStats(any()) } returns listOf(pool("BTC.BTC"))
        coEvery { api.getLiquidityProvider("BTC.BTC", RUNE_ADDR) } throws
            IllegalStateException("unexpected")

        assertFailsWith<IllegalStateException> { useCase(runeAddress = RUNE_ADDR) }
    }

    @Test
    fun `tolerates non-numeric apr from midgard`() = runTest {
        coEvery { api.getPoolStats(any()) } returns listOf(pool("BTC.BTC", apr = "NaN"))
        coEvery { api.getLiquidityProvider("BTC.BTC", RUNE_ADDR) } returns lp(units = "1")

        val position = useCase(runeAddress = RUNE_ADDR).single()

        assertTrue(position.annualPercentageRate == null)
    }

    private fun pool(
        asset: String,
        status: String = "available",
        apr: String? = null,
    ): ThorChainPoolStatsJson =
        ThorChainPoolStatsJson(asset = asset, status = status, annualPercentageRate = apr)

    private fun lp(
        units: String,
        runeRedeem: String = "0",
        assetRedeem: String = "0",
    ): ThorChainLiquidityProviderJson =
        ThorChainLiquidityProviderJson(
            asset = "any",
            units = units,
            runeRedeemValue = runeRedeem,
            assetRedeemValue = assetRedeem,
        )

    private companion object {
        const val RUNE_ADDR = "thor1aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
        const val BTC_ADDR = "bc1qxyz"
        const val ETH_ADDR = "0xabc"
    }
}

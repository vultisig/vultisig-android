package com.vultisig.wallet.data.usecases

import com.vultisig.wallet.data.api.ThorChainApi
import com.vultisig.wallet.data.api.models.thorchain.ThorChainLiquidityProviderJson
import com.vultisig.wallet.data.utils.NetworkException
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import java.math.BigInteger
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

internal class GetThorChainLpPositionUseCaseTest {

    private lateinit var api: ThorChainApi
    private lateinit var useCase: GetThorChainLpPositionUseCaseImpl

    @BeforeEach
    fun setUp() {
        api = mockk()
        useCase = GetThorChainLpPositionUseCaseImpl(api)
    }

    @Test
    fun `returns position from rune-side lookup without touching pool stats`() = runTest {
        coEvery { api.getLiquidityProvider(POOL, RUNE_ADDR) } returns
            lp(units = "1000", runeRedeem = "1500", assetRedeem = "2500")

        val position = useCase(poolId = POOL, runeAddress = RUNE_ADDR)

        assertEquals(POOL, position?.pool)
        assertEquals(BigInteger("1000"), position?.units)
        assertEquals(BigInteger("1500"), position?.runeRedeemValue)
        assertEquals(BigInteger("2500"), position?.assetRedeemValue)
        assertNull(position?.annualPercentageRate)
        coVerify(exactly = 0) { api.getPoolStats(any()) }
        coVerify(exactly = 0) { api.getLiquidityProvider(neq(POOL), any()) }
    }

    @Test
    fun `falls back to asset address when rune-side has no record`() = runTest {
        coEvery { api.getLiquidityProvider(POOL, RUNE_ADDR) } returns null
        coEvery { api.getLiquidityProvider(POOL, ASSET_ADDR) } returns lp(units = "42")

        val position = useCase(poolId = POOL, runeAddress = RUNE_ADDR, assetAddress = ASSET_ADDR)

        assertEquals(BigInteger("42"), position?.units)
        coVerify { api.getLiquidityProvider(POOL, ASSET_ADDR) }
    }

    @Test
    fun `returns null when no position exists for either side`() = runTest {
        coEvery { api.getLiquidityProvider(POOL, RUNE_ADDR) } returns null
        coEvery { api.getLiquidityProvider(POOL, ASSET_ADDR) } returns null

        val position = useCase(poolId = POOL, runeAddress = RUNE_ADDR, assetAddress = ASSET_ADDR)

        assertNull(position)
    }

    @Test
    fun `returns null when units are zero`() = runTest {
        coEvery { api.getLiquidityProvider(POOL, RUNE_ADDR) } returns lp(units = "0")

        val position = useCase(poolId = POOL, runeAddress = RUNE_ADDR)

        assertNull(position)
    }

    @Test
    fun `does not query asset address when not provided`() = runTest {
        coEvery { api.getLiquidityProvider(POOL, RUNE_ADDR) } returns null

        val position = useCase(poolId = POOL, runeAddress = RUNE_ADDR)

        assertNull(position)
        coVerify(exactly = 1) { api.getLiquidityProvider(POOL, any()) }
    }

    @Test
    fun `swallows network errors and returns null`() = runTest {
        coEvery { api.getLiquidityProvider(POOL, RUNE_ADDR) } throws
            NetworkException(httpStatusCode = 500, message = "boom")

        val position = useCase(poolId = POOL, runeAddress = RUNE_ADDR)

        assertNull(position)
    }

    @Test
    fun `propagates unexpected exceptions`() = runTest {
        coEvery { api.getLiquidityProvider(POOL, RUNE_ADDR) } throws
            IllegalStateException("unexpected")

        assertFailsWith<IllegalStateException> { useCase(poolId = POOL, runeAddress = RUNE_ADDR) }
    }

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
        const val POOL = "BTC.BTC"
        const val RUNE_ADDR = "thor1aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
        const val ASSET_ADDR = "bc1qxyz"
    }
}

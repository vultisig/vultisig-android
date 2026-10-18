package com.vultisig.wallet.data.usecases

import com.vultisig.wallet.data.api.ThorChainApi
import com.vultisig.wallet.data.api.models.thorchain.ThorChainLiquidityProviderJson
import com.vultisig.wallet.data.api.models.thorchain.ThorChainPoolJson
import com.vultisig.wallet.data.utils.NetworkException
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import java.math.BigInteger
import kotlin.test.assertFailsWith
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

internal class GetThorChainPendingLpDepositsUseCaseTest {

    private lateinit var api: ThorChainApi
    private lateinit var useCase: GetThorChainPendingLpDepositsUseCaseImpl

    @BeforeEach
    fun setUp() {
        api = mockk()
        useCase = GetThorChainPendingLpDepositsUseCaseImpl(api)
        coEvery { api.getMimir() } returns mapOf("PENDINGLIQUIDITYAGELIMIT" to AGE_LIMIT)
        coEvery { api.getLastBlock() } returns CURRENT_HEIGHT
    }

    @Test
    fun `returns pending deposits with a refund countdown`() = runTest {
        coEvery { api.getPools() } returns
            listOf(pool("BTC.BTC", pendingRune = "100"), pool("ETH.ETH"))
        coEvery { api.getLiquidityProvider("BTC.BTC", RUNE_ADDR) } returns
            lp("BTC.BTC", pendingRune = "100", lastAddHeight = LAST_ADD_HEIGHT)

        val deposits = useCase(RUNE_ADDR)

        val btc = deposits.single()
        assertEquals("BTC.BTC", btc.pool)
        assertEquals(BigInteger("100"), btc.pendingRune)
        assertEquals(BTC_ADDR, btc.pairedAddress)
        assertEquals(LAST_ADD_HEIGHT + AGE_LIMIT - CURRENT_HEIGHT, btc.blocksUntilRefund)
        coVerify(exactly = 0) { api.getLiquidityProvider("ETH.ETH", any()) }
    }

    @Test
    fun `throws when the pool scan fails`() = runTest {
        coEvery { api.getPools() } throws NetworkException(503, "unavailable")

        assertFailsWith<NetworkException> { useCase(RUNE_ADDR) }
    }

    @Test
    fun `throws when one candidate pool's read fails`() = runTest {
        coEvery { api.getPools() } returns
            listOf(pool("BTC.BTC", pendingRune = "100"), pool("ETH.ETH", pendingAsset = "5"))
        coEvery { api.getLiquidityProvider("BTC.BTC", RUNE_ADDR) } returns
            lp("BTC.BTC", pendingRune = "100")
        coEvery { api.getLiquidityProvider("ETH.ETH", RUNE_ADDR) } throws
            NetworkException(429, "rate limited")

        assertFailsWith<NetworkException> { useCase(RUNE_ADDR) }
    }

    @Test
    fun `skips a pool with no record for the user`() = runTest {
        coEvery { api.getPools() } returns
            listOf(pool("BTC.BTC", pendingRune = "100"), pool("ETH.ETH", pendingRune = "7"))
        coEvery { api.getLiquidityProvider("BTC.BTC", RUNE_ADDR) } returns null
        coEvery { api.getLiquidityProvider("ETH.ETH", RUNE_ADDR) } returns
            lp("ETH.ETH", pendingRune = "7")

        val deposits = useCase(RUNE_ADDR)

        assertEquals(listOf("ETH.ETH"), deposits.map { it.pool })
    }

    @Test
    fun `returns nothing when no pool holds pending liquidity`() = runTest {
        coEvery { api.getPools() } returns listOf(pool("BTC.BTC"))

        assertTrue(useCase(RUNE_ADDR).isEmpty())
        coVerify(exactly = 0) { api.getLiquidityProvider(any(), any()) }
    }

    @Test
    fun `a failed countdown read still returns the deposits`() = runTest {
        coEvery { api.getPools() } returns listOf(pool("BTC.BTC", pendingRune = "100"))
        coEvery { api.getLiquidityProvider("BTC.BTC", RUNE_ADDR) } returns
            lp("BTC.BTC", pendingRune = "100", lastAddHeight = LAST_ADD_HEIGHT)
        coEvery { api.getMimir() } throws NetworkException(500, "mimir down")
        coEvery { api.getLastBlock() } throws NetworkException(500, "height down")

        val btc = useCase(RUNE_ADDR).single()

        assertEquals("BTC.BTC", btc.pool)
        assertNull(btc.blocksUntilRefund)
    }

    private fun pool(asset: String, pendingRune: String = "0", pendingAsset: String = "0") =
        ThorChainPoolJson(
            asset = asset,
            assetTorPrice = BigInteger.ONE,
            pendingInboundRune = pendingRune,
            pendingInboundAsset = pendingAsset,
        )

    private fun lp(asset: String, pendingRune: String, lastAddHeight: Long? = null) =
        ThorChainLiquidityProviderJson(
            asset = asset,
            runeAddress = RUNE_ADDR,
            assetAddress = BTC_ADDR,
            pendingRune = pendingRune,
            lastAddHeight = lastAddHeight,
        )

    private companion object {
        const val RUNE_ADDR = "thor1user"
        const val BTC_ADDR = "bc1user"
        const val AGE_LIMIT = 100_800L
        const val CURRENT_HEIGHT = 20_000_000L
        const val LAST_ADD_HEIGHT = 19_990_000L
    }
}

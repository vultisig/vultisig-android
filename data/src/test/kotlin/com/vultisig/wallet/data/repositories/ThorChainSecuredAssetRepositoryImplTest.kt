package com.vultisig.wallet.data.repositories

import com.vultisig.wallet.data.api.ThorChainApi
import com.vultisig.wallet.data.api.models.thorchain.ThorChainPoolJson
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.isSecuredAsset
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import java.math.BigInteger
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class ThorChainSecuredAssetRepositoryImplTest {

    private val thorChainApi: ThorChainApi = mockk()

    private fun repo() = ThorChainSecuredAssetRepositoryImpl(thorChainApi)

    private fun pool(asset: String, status: String = "Available") =
        ThorChainPoolJson(asset = asset, assetTorPrice = BigInteger.ONE, status = status)

    @Test
    fun `derives the secured-asset denom and ticker from a native L1 pool`() = runTest {
        coEvery { thorChainApi.getPools() } returns listOf(pool("BTC.BTC"))

        val coins = repo().getSecuredAssetCoins()

        val coin = coins.single()
        assertEquals(Chain.ThorChain, coin.chain)
        assertEquals("BTC", coin.ticker)
        assertEquals("btc-btc", coin.contractAddress)
        assertEquals(8, coin.decimal)
        assertTrue(coin.isSecuredAsset())
    }

    @Test
    fun `preserves the contract tail for an ERC20-style pool asset`() = runTest {
        coEvery { thorChainApi.getPools() } returns
            listOf(pool("ETH.USDC-0xA0b86991c6218b36c1d19d4a2e9eb0Ce3606eB48"))

        val coin = repo().getSecuredAssetCoins().single()

        assertEquals("USDC", coin.ticker)
        assertEquals("eth-usdc-0xa0b86991c6218b36c1d19d4a2e9eb0ce3606eb48", coin.contractAddress)
    }

    @Test
    fun `excludes THOR-native pools such as TCY`() = runTest {
        coEvery { thorChainApi.getPools() } returns listOf(pool("THOR.TCY"), pool("BTC.BTC"))

        val coins = repo().getSecuredAssetCoins()

        assertEquals(1, coins.size)
        assertEquals("BTC", coins.single().ticker)
    }

    @Test
    fun `excludes pools that are not Available`() = runTest {
        coEvery { thorChainApi.getPools() } returns
            listOf(pool("ETH.ETH", status = "Staged"), pool("BTC.BTC", status = "available"))

        val coins = repo().getSecuredAssetCoins()

        assertEquals(listOf("BTC"), coins.map { it.ticker })
    }

    @Test
    fun `drops malformed pool asset ids`() = runTest {
        coEvery { thorChainApi.getPools() } returns
            listOf(pool(".USDT"), pool("ETH."), pool("NODOT"), pool("BTC.BTC"))

        val coins = repo().getSecuredAssetCoins()

        assertEquals(listOf("BTC"), coins.map { it.ticker })
    }

    @Test
    fun `a fresh successful snapshot is not re-fetched within the TTL window`() = runTest {
        coEvery { thorChainApi.getPools() } returns listOf(pool("BTC.BTC"))
        val repo = repo()

        repeat(5) { repo.getSecuredAssetCoins() }

        coVerify(exactly = 1) { thorChainApi.getPools() }
    }

    @Test
    fun `returns an empty catalog and does not throw when the very first fetch fails`() = runTest {
        coEvery { thorChainApi.getPools() } throws RuntimeException("boom")

        val coins = repo().getSecuredAssetCoins()

        assertTrue(coins.isEmpty())
    }
}

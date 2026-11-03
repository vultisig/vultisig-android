package com.vultisig.wallet.data.repositories

import com.vultisig.wallet.data.api.ThorChainApi
import com.vultisig.wallet.data.api.models.thorchain.ThorChainPoolJson
import com.vultisig.wallet.data.api.models.thorchain.ThorChainSecuredAssetJson
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.isSecuredAsset
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import java.math.BigInteger
import kotlin.time.TestTimeSource
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class ThorChainSecuredAssetRepositoryImplTest {

    private val thorChainApi: ThorChainApi = mockk()

    private fun repo() = ThorChainSecuredAssetRepositoryImpl(thorChainApi, TestTimeSource())

    private fun securedAsset(asset: String) = ThorChainSecuredAssetJson(asset = asset)

    private fun pool(asset: String, status: String = "Available") =
        ThorChainPoolJson(asset = asset, assetTorPrice = BigInteger.ONE, status = status)

    @Test
    fun `derives the secured-asset denom and ticker from a native L1 secured asset`() = runTest {
        coEvery { thorChainApi.getSecuredAssets() } returns listOf(securedAsset("BTC-BTC"))
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
    fun `preserves the contract tail for an ERC20-style secured asset`() = runTest {
        coEvery { thorChainApi.getSecuredAssets() } returns
            listOf(securedAsset("ETH-USDC-0XA0B86991C6218B36C1D19D4A2E9EB0CE3606EB48"))
        coEvery { thorChainApi.getPools() } returns
            listOf(pool("ETH.USDC-0xA0b86991c6218b36c1d19d4a2e9eb0Ce3606eB48"))

        val coin = repo().getSecuredAssetCoins().single()

        assertEquals("USDC", coin.ticker)
        assertEquals("eth-usdc-0xa0b86991c6218b36c1d19d4a2e9eb0ce3606eb48", coin.contractAddress)
    }

    @Test
    fun `excludes THOR-native assets such as TCY`() = runTest {
        coEvery { thorChainApi.getSecuredAssets() } returns
            listOf(securedAsset("THOR-TCY"), securedAsset("BTC-BTC"))
        coEvery { thorChainApi.getPools() } returns listOf(pool("THOR.TCY"), pool("BTC.BTC"))

        val coins = repo().getSecuredAssetCoins()

        assertEquals(1, coins.size)
        assertEquals("BTC", coins.single().ticker)
    }

    @Test
    fun `excludes pools that are not Available`() = runTest {
        coEvery { thorChainApi.getSecuredAssets() } returns
            listOf(securedAsset("ETH-ETH"), securedAsset("BTC-BTC"))
        coEvery { thorChainApi.getPools() } returns
            listOf(pool("ETH.ETH", status = "Staged"), pool("BTC.BTC", status = "available"))

        val coins = repo().getSecuredAssetCoins()

        assertEquals(listOf("BTC"), coins.map { it.ticker })
    }

    @Test
    fun `drops malformed secured-asset denoms`() = runTest {
        coEvery { thorChainApi.getSecuredAssets() } returns
            listOf(
                securedAsset("-USDT"),
                securedAsset("ETH-"),
                securedAsset("NODASH"),
                securedAsset("BTC-BTC"),
            )
        coEvery { thorChainApi.getPools() } returns listOf(pool("BTC.BTC"))

        val coins = repo().getSecuredAssetCoins()

        assertEquals(listOf("BTC"), coins.map { it.ticker })
    }

    @Test
    fun `a fresh successful snapshot is not re-fetched within the TTL window`() = runTest {
        coEvery { thorChainApi.getSecuredAssets() } returns listOf(securedAsset("BTC-BTC"))
        coEvery { thorChainApi.getPools() } returns listOf(pool("BTC.BTC"))
        val repo = repo()

        repeat(5) { repo.getSecuredAssetCoins() }

        coVerify(exactly = 1) { thorChainApi.getSecuredAssets() }
        coVerify(exactly = 1) { thorChainApi.getPools() }
    }

    @Test
    fun `falls back to the static catalog when the very first fetch fails`() = runTest {
        coEvery { thorChainApi.getSecuredAssets() } throws RuntimeException("boom")
        coEvery { thorChainApi.getPools() } returns listOf(pool("BTC.BTC"))

        val coins = repo().getSecuredAssetCoins()

        assertEquals(
            setOf("BTC", "ETH", "BCH", "LTC", "DOGE", "AVAX", "BNB"),
            coins.map { it.ticker }.toSet(),
        )
        assertTrue(coins.all { it.isSecuredAsset() })
    }

    @Test
    fun `the static fallback uses THORChain's BSC chain code for BNB, not the ticker`() = runTest {
        coEvery { thorChainApi.getSecuredAssets() } throws RuntimeException("boom")
        coEvery { thorChainApi.getPools() } returns listOf(pool("BTC.BTC"))

        val coins = repo().getSecuredAssetCoins()

        assertEquals("bsc-bnb", coins.single { it.ticker == "BNB" }.contractAddress)
    }

    @Test
    fun `a later failure keeps the live snapshot instead of reverting to the static fallback`() =
        runTest {
            coEvery { thorChainApi.getSecuredAssets() } returns listOf(securedAsset("ETH-ETH"))
            coEvery { thorChainApi.getPools() } returns listOf(pool("ETH.ETH"))
            val repo = repo()
            repo.refresh()
            val liveSnapshot = repo.getSecuredAssetCoins()
            assertEquals(listOf("ETH"), liveSnapshot.map { it.ticker })

            // refresh() bypasses the TTL, forcing a real re-fetch attempt that then fails.
            coEvery { thorChainApi.getSecuredAssets() } throws RuntimeException("boom")
            repo.refresh()

            assertEquals(liveSnapshot, repo.getSecuredAssetCoins())
        }
}

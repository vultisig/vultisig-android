package com.vultisig.wallet.data.repositories

import com.vultisig.wallet.data.api.CoinGeckoApi
import com.vultisig.wallet.data.api.models.CoinMarketStatsJson
import com.vultisig.wallet.data.api.models.MarketChartResponseJson
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.ChartRange
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.settings.AppCurrency
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import java.math.BigDecimal
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

internal class TokenPriceChartRepositoryImplTest {

    private lateinit var coinGeckoApi: CoinGeckoApi
    private lateinit var repository: TokenPriceChartRepositoryImpl

    @BeforeEach
    fun setUp() {
        coinGeckoApi = mockk()
        repository = TokenPriceChartRepositoryImpl(coinGeckoApi)
    }

    private fun coin(priceProviderId: String = "", contractAddress: String = "") =
        Coin(
            chain = Chain.Ethereum,
            ticker = "TOK",
            logo = "",
            address = "",
            decimal = 18,
            hexPublicKey = "",
            priceProviderID = priceProviderId,
            contractAddress = contractAddress,
            isNativeToken = false,
        )

    @Test
    fun `getChart returns null for a coin with no priceProviderId and no contractAddress`() =
        runTest {
            val poolToken = coin()

            val chart = repository.getChart(poolToken, ChartRange.ONE_DAY, AppCurrency.USD)

            assertNull(chart)
            coVerify(exactly = 0) { coinGeckoApi.getMarketChart(any(), any(), any()) }
            coVerify(exactly = 0) {
                coinGeckoApi.getContractMarketChart(any(), any(), any(), any())
            }
        }

    @Test
    fun `getChart routes by priceProviderId when present`() = runTest {
        val token = coin(priceProviderId = "ethereum", contractAddress = "0xabc")
        coEvery { coinGeckoApi.getMarketChart("ethereum", "usd", "1") } returns
            MarketChartResponseJson(prices = listOf(listOf(1_000.0, 100.0), listOf(2_000.0, 110.0)))

        val chart = repository.getChart(token, ChartRange.ONE_DAY, AppCurrency.USD)

        requireNotNull(chart)
        assertEquals(2, chart.points.size)
        assertEquals(true, chart.isPositive)
        coVerify(exactly = 0) { coinGeckoApi.getContractMarketChart(any(), any(), any(), any()) }
    }

    @Test
    fun `getChart routes by contract address when priceProviderId is empty`() = runTest {
        val token = coin(priceProviderId = "", contractAddress = "0xabc")
        coEvery { coinGeckoApi.getContractMarketChart(Chain.Ethereum, "0xabc", "usd", "1") } returns
            MarketChartResponseJson(prices = listOf(listOf(1_000.0, 100.0), listOf(2_000.0, 90.0)))

        val chart = repository.getChart(token, ChartRange.ONE_DAY, AppCurrency.USD)

        requireNotNull(chart)
        assertEquals(false, chart.isPositive)
        coVerify(exactly = 0) { coinGeckoApi.getMarketChart(any(), any(), any()) }
    }

    @Test
    fun `getChart caches within the range's TTL, avoiding a second network call`() = runTest {
        val token = coin(priceProviderId = "ethereum")
        coEvery { coinGeckoApi.getMarketChart(any(), any(), any()) } returns
            MarketChartResponseJson(prices = listOf(listOf(1_000.0, 100.0), listOf(2_000.0, 100.0)))

        repository.getChart(token, ChartRange.ONE_DAY, AppCurrency.USD)
        repository.getChart(token, ChartRange.ONE_DAY, AppCurrency.USD)

        coVerify(exactly = 1) { coinGeckoApi.getMarketChart(any(), any(), any()) }
    }

    @Test
    fun `getChart returns null (no crash) when the network call fails and nothing was cached yet`() =
        runTest {
            val token = coin(priceProviderId = "ethereum")
            coEvery { coinGeckoApi.getMarketChart(any(), any(), any()) } throws
                RuntimeException("network down")

            val chart = repository.getChart(token, ChartRange.ONE_DAY, AppCurrency.USD)

            assertNull(chart)
        }

    @Test
    fun `getChart returns null for a genuinely successful response with fewer than 2 points`() =
        runTest {
            val token = coin(priceProviderId = "ethereum")
            coEvery { coinGeckoApi.getMarketChart(any(), any(), any()) } returns
                MarketChartResponseJson(prices = listOf(listOf(1_000.0, 100.0)))

            val chart = repository.getChart(token, ChartRange.ONE_DAY, AppCurrency.USD)

            assertNull(chart)
        }

    @Test
    fun `getStats returns null (no crash) when the network call fails`() = runTest {
        val token = coin(priceProviderId = "ethereum")
        coEvery { coinGeckoApi.getMarketStats(any(), any()) } throws
            RuntimeException("network down")

        val stats = repository.getStats(token, AppCurrency.USD)

        assertNull(stats)
    }

    @Test
    fun `getStats returns null when priceProviderId is empty`() = runTest {
        val token = coin(priceProviderId = "", contractAddress = "0xabc")

        val stats = repository.getStats(token, AppCurrency.USD)

        assertNull(stats)
        coVerify(exactly = 0) { coinGeckoApi.getMarketStats(any(), any()) }
    }

    @Test
    fun `getStats maps the first markets entry into the domain model`() = runTest {
        val token = coin(priceProviderId = "ethereum")
        coEvery { coinGeckoApi.getMarketStats("ethereum", "usd") } returns
            listOf(
                CoinMarketStatsJson(
                    marketCap = BigDecimal("1000000"),
                    marketCapRank = 2,
                    circulatingSupply = BigDecimal("120000000"),
                )
            )

        val stats = repository.getStats(token, AppCurrency.USD)

        requireNotNull(stats)
        assertEquals(BigDecimal("1000000"), stats.marketCap)
        assertEquals(2, stats.marketCapRank)
        assertEquals(BigDecimal("120000000"), stats.circulatingSupply)
    }

    @Test
    fun `getStats returns null when the markets response is an empty array`() = runTest {
        val token = coin(priceProviderId = "ethereum")
        coEvery { coinGeckoApi.getMarketStats("ethereum", "usd") } returns emptyList()

        val stats = repository.getStats(token, AppCurrency.USD)

        assertNull(stats)
    }

    @Test
    fun `getStats nulls a malformed ath_date without dropping the ath price or throwing`() =
        runTest {
            val token = coin(priceProviderId = "ethereum")
            coEvery { coinGeckoApi.getMarketStats("ethereum", "usd") } returns
                listOf(CoinMarketStatsJson(ath = BigDecimal("100"), athDate = "not-a-date"))

            val stats = repository.getStats(token, AppCurrency.USD)

            requireNotNull(stats)
            assertEquals(BigDecimal("100"), stats.athPrice)
            assertNull(stats.athDate)
        }
}

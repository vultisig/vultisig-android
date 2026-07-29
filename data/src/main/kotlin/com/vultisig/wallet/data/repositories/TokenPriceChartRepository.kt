package com.vultisig.wallet.data.repositories

import com.vultisig.wallet.data.api.CoinGeckoApi
import com.vultisig.wallet.data.api.models.CoinMarketStatsJson
import com.vultisig.wallet.data.models.ChartRange
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.CoinMarketStats
import com.vultisig.wallet.data.models.MarketChart
import com.vultisig.wallet.data.models.hasMarketDataSource
import com.vultisig.wallet.data.models.settings.AppCurrency
import com.vultisig.wallet.data.utils.DEFAULT_MAX_CHART_POINTS
import com.vultisig.wallet.data.utils.TtlCache
import com.vultisig.wallet.data.utils.changePercent
import com.vultisig.wallet.data.utils.decodeMarketChartPoints
import com.vultisig.wallet.data.utils.downsampleChartPoints
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import timber.log.Timber

interface TokenPriceChartRepository {

    /**
     * Returns the price series for [coin] over [range] in [currency], or null when the coin has no
     * CoinGecko source ([Coin.hasMarketDataSource] is false), CoinGecko has fewer than 2 points of
     * history for it (too new/unindexed to plot a line), or the fetch failed with nothing cached to
     * fall back on.
     */
    suspend fun getChart(coin: Coin, range: ChartRange, currency: AppCurrency): MarketChart?

    /**
     * Returns market stats/price-extremes for [coin] in [currency], or null when the coin has no
     * CoinGecko price-provider id (CoinGecko's `/coins/markets` has no contract-address variant) or
     * the fetch failed with nothing cached to fall back on.
     */
    suspend fun getStats(coin: Coin, currency: AppCurrency): CoinMarketStats?
}

private data class ChartCacheKey(val coinId: String, val range: ChartRange, val currency: String)

private data class StatsCacheKey(val coinId: String, val currency: String)

internal class TokenPriceChartRepositoryImpl
@Inject
constructor(private val coinGeckoApi: CoinGeckoApi) : TokenPriceChartRepository {

    private val chartCache = TtlCache<ChartCacheKey, MarketChart>()
    private val statsCache = TtlCache<StatsCacheKey, CoinMarketStats>()

    override suspend fun getChart(
        coin: Coin,
        range: ChartRange,
        currency: AppCurrency,
    ): MarketChart? {
        if (!coin.hasMarketDataSource) return null
        val key = ChartCacheKey(coin.id, range, currency.ticker)
        val result =
            try {
                chartCache.getOrPut(key, range.cacheTtlMillis) { fetchChart(coin, range, currency) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                Timber.e(e, "Failed to fetch market chart for %s", coin.id)
                // Fail open to the last-good series rather than a blank/error chart.
                chartCache.peekStale(key)
            }
        // A brand-new or delisted coin can genuinely 200 with 0-1 price points; PriceChartCanvas
        // needs at least 2 to draw a line, so treat that the same as "no data" rather than an
        // empty-but-non-null chart.
        return result?.takeIf { it.points.size >= 2 }
    }

    private suspend fun fetchChart(
        coin: Coin,
        range: ChartRange,
        currency: AppCurrency,
    ): MarketChart {
        val currencyTicker = currency.ticker.lowercase()
        val response =
            if (coin.priceProviderID.isNotEmpty()) {
                coinGeckoApi.getMarketChart(coin.priceProviderID, currencyTicker, range.days)
            } else {
                coinGeckoApi.getContractMarketChart(
                    coin.chain,
                    coin.contractAddress,
                    currencyTicker,
                    range.days,
                )
            }
        val points =
            downsampleChartPoints(
                decodeMarketChartPoints(response.prices),
                DEFAULT_MAX_CHART_POINTS,
            )
        return MarketChart(points = points, changePercent = changePercent(points))
    }

    override suspend fun getStats(coin: Coin, currency: AppCurrency): CoinMarketStats? {
        if (coin.priceProviderID.isEmpty()) return null
        val key = StatsCacheKey(coin.id, currency.ticker)
        return try {
            statsCache.getOrPut(key, STATS_CACHE_TTL_MILLIS) { fetchStats(coin, currency) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            Timber.e(e, "Failed to fetch market stats for %s", coin.id)
            statsCache.peekStale(key)
        }
    }

    private suspend fun fetchStats(coin: Coin, currency: AppCurrency): CoinMarketStats {
        val json =
            coinGeckoApi
                .getMarketStats(coin.priceProviderID, currency.ticker.lowercase())
                .firstOrNull() ?: error("No market stats for ${coin.priceProviderID}")
        return json.toDomain()
    }

    private companion object {
        private const val STATS_CACHE_TTL_MILLIS = 60_000L
    }
}

private fun CoinMarketStatsJson.toDomain(): CoinMarketStats =
    CoinMarketStats(
        marketCap = marketCap,
        marketCapRank = marketCapRank,
        fullyDilutedValuation = fullyDilutedValuation,
        volume24h = totalVolume,
        circulatingSupply = circulatingSupply,
        maxSupply = maxSupply,
        low24h = low24h,
        high24h = high24h,
        athPrice = ath,
        athDate = athDate?.toInstantOrNull(),
        athChangePercent = athChangePercentage,
        atlPrice = atl,
        atlDate = atlDate?.toInstantOrNull(),
        atlChangePercent = atlChangePercentage,
    )

private fun String.toInstantOrNull(): Instant? = runCatching { Instant.parse(this) }.getOrNull()

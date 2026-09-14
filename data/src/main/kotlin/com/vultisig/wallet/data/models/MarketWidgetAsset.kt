package com.vultisig.wallet.data.models

import kotlin.math.roundToLong
import kotlinx.serialization.Serializable

/**
 * One market record rendered by the home-screen market widgets. Deliberately a small, standalone
 * shape — the widgets never touch vaults, coins, or key shares, only public CoinGecko market data.
 */
@Serializable
data class MarketWidgetAsset(
    val id: String,
    val symbol: String,
    val name: String,
    val imageUrl: String?,
    val currentPrice: Double,
    val priceChangePercentage24h: Double?,
    val marketCapRank: Int?,
    /** Seven-day price series, already down-sampled to [MarketWidgetQuery.SPARKLINE_POINTS]. */
    val sparkline: List<Double>,
) {
    /**
     * 24h change rounded to the two decimals the widgets display, or null when CoinGecko has none
     * or sent a non-finite value. Rounding here keeps sign, colour and text in agreement: a -0.004%
     * move renders as "+0.00%" in the positive colour instead of a red "-0.00%".
     */
    val finiteChange24h: Double?
        get() =
            priceChangePercentage24h
                ?.takeIf { it.isFinite() }
                ?.let { (it * 100).roundToLong() / 100.0 }
}

/** Identity-only record used by the widget asset picker (search results and suggestions). */
data class MarketWidgetAssetIdentity(val id: String, val symbol: String, val name: String)

data class MarketWidgetResult(
    val assets: List<MarketWidgetAsset>,
    /** Epoch millis of the fetch that produced [assets]. */
    val updatedAt: Long,
    /** True when [assets] came from the last-good cache because the refresh failed. */
    val isStale: Boolean,
)

sealed interface MarketWidgetQuery {

    /** Top assets by market cap, at most [MAX_ASSETS]. */
    data class Top(val count: Int) : MarketWidgetQuery

    /** Specific CoinGecko ids, at most [MAX_ASSETS], rendered in the given order. */
    data class Ids(val ids: List<String>) : MarketWidgetQuery

    val normalizedIds: List<String>
        get() =
            when (this) {
                is Top -> emptyList()
                is Ids ->
                    ids.map { it.trim().lowercase() }
                        .filter { it.isNotEmpty() }
                        .distinct()
                        .take(MAX_ASSETS)
            }

    val limit: Int
        get() =
            when (this) {
                is Top -> count.coerceIn(1, MAX_ASSETS)
                is Ids -> normalizedIds.size.coerceIn(1, MAX_ASSETS)
            }

    val cacheKey: String
        get() =
            when (this) {
                is Top -> "top-$limit"
                is Ids -> "ids-${normalizedIds.joinToString(",")}"
            }

    companion object {
        const val MAX_ASSETS = 5
        const val SPARKLINE_POINTS = 28
    }
}

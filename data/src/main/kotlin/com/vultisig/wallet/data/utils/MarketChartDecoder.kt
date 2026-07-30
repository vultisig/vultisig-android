package com.vultisig.wallet.data.utils

import com.vultisig.wallet.data.models.MarketChartPoint
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Decodes CoinGecko's `market_chart` `prices` array (`[[msEpoch, price], ...]`) into
 * [MarketChartPoint]s, sorted ascending by time. Sub-arrays with fewer than 2 elements are dropped
 * rather than crashing — CoinGecko's free tier has been observed to return short/malformed entries.
 */
fun decodeMarketChartPoints(raw: List<List<Double>>): List<MarketChartPoint> =
    raw.mapNotNull { pair ->
            if (pair.size < 2) return@mapNotNull null
            MarketChartPoint(
                timestampMillis = pair[0].toLong(),
                price = BigDecimal.valueOf(pair[1]),
            )
        }
        .sortedBy { it.timestampMillis }

/**
 * The percent change from the first to the last point in [points], used to tint the chart and show
 * the range's headline change. Zero when there are fewer than 2 points or the first price is zero
 * (avoids a division by zero).
 */
fun changePercent(points: List<MarketChartPoint>): Double {
    val first = points.firstOrNull()?.price ?: return 0.0
    val last = points.lastOrNull()?.price ?: return 0.0
    if (first.signum() == 0) return 0.0
    return last
        .subtract(first)
        .divide(first, 10, RoundingMode.HALF_UP)
        .multiply(BigDecimal(100))
        .toDouble()
}

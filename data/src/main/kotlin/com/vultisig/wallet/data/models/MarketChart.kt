package com.vultisig.wallet.data.models

import java.math.BigDecimal

/** A single price sample: milliseconds since epoch paired with the price at that time. */
data class MarketChartPoint(val timestampMillis: Long, val price: BigDecimal)

/**
 * A downsampled price series for one [ChartRange], with the percent change from the first to the
 * last point — used to tint the chart green/red and show the range's headline change.
 */
data class MarketChart(val points: List<MarketChartPoint>, val changePercent: Double) {
    val isPositive: Boolean
        get() = changePercent >= 0.0
}

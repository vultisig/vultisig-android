package com.vultisig.wallet.data.utils

/**
 * Downsamples [points] to at most [maxPoints] entries, always preserving the first and last point.
 * CoinGecko's free tier derives point density from the requested range (169 points for 1W, up to
 * ~4800 for ALL), so the chart/scrub UI needs a bounded, evenly-spaced series regardless of range.
 */
fun <T> downsampleChartPoints(points: List<T>, maxPoints: Int = DEFAULT_MAX_CHART_POINTS): List<T> {
    require(maxPoints >= 2) { "maxPoints must be at least 2" }
    if (points.size <= maxPoints) return points

    val lastIndex = points.size - 1
    val result =
        List(maxPoints) { i -> points[(i * lastIndex.toDouble() / (maxPoints - 1)).toInt()] }
            .toMutableList()
    result[0] = points[0]
    result[result.lastIndex] = points[lastIndex]
    return result
}

const val DEFAULT_MAX_CHART_POINTS = 300

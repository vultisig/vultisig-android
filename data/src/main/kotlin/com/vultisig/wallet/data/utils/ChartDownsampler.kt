package com.vultisig.wallet.data.utils

/**
 * Downsamples [points] to at most [maxPoints] entries, always preserving the first and last point.
 * CoinGecko's free tier derives point density from the requested range (169 points for 1W, up to
 * ~4800 for ALL), so the chart/scrub UI needs a bounded, evenly-spaced series regardless of range.
 *
 * The generator expression below already lands exactly on index 0 for `i=0` and exactly on the last
 * index for `i=maxPoints-1`: both products stay well within a Double's exact-integer range for any
 * realistic point count, so IEEE-754 division introduces no rounding there — no separate first/last
 * overwrite is needed.
 */
fun <T> downsampleChartPoints(points: List<T>, maxPoints: Int = DEFAULT_MAX_CHART_POINTS): List<T> {
    require(maxPoints >= 2) { "maxPoints must be at least 2" }
    if (points.size <= maxPoints) return points

    val lastIndex = points.size - 1
    return List(maxPoints) { i -> points[(i * lastIndex.toDouble() / (maxPoints - 1)).toInt()] }
}

const val DEFAULT_MAX_CHART_POINTS = 300

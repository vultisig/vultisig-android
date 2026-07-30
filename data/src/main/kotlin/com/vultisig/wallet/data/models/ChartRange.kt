package com.vultisig.wallet.data.models

/**
 * Time ranges offered by the token detail price chart. [days] is CoinGecko's `market_chart` `days`
 * query parameter; [cacheTtlMillis] is how long a fetched series for this range is considered fresh
 * before a re-fetch is attempted (short for 1D, longer for 1Y/ALL, since older data changes less).
 * Display labels live in the `chart_range_*` string resources, not here.
 */
enum class ChartRange(val days: String, val cacheTtlMillis: Long) {
    ONE_DAY(days = "1", cacheTtlMillis = 60_000L),
    ONE_WEEK(days = "7", cacheTtlMillis = 600_000L),
    ONE_MONTH(days = "30", cacheTtlMillis = 600_000L),
    ONE_YEAR(days = "365", cacheTtlMillis = 3_600_000L),
    ALL(days = "max", cacheTtlMillis = 3_600_000L),
}

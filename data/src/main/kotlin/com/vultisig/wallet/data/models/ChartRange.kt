package com.vultisig.wallet.data.models

/**
 * Time ranges offered by the token detail price chart. [days] is CoinGecko's `market_chart` `days`
 * query parameter; [cacheTtlMillis] is how long a fetched series for this range is considered fresh
 * before a re-fetch is attempted (short for 1D, longer for 1Y/ALL, since older data changes less).
 */
enum class ChartRange(val label: String, val days: String, val cacheTtlMillis: Long) {
    ONE_DAY(label = "1D", days = "1", cacheTtlMillis = 60_000L),
    ONE_WEEK(label = "1W", days = "7", cacheTtlMillis = 600_000L),
    ONE_MONTH(label = "1M", days = "30", cacheTtlMillis = 600_000L),
    ONE_YEAR(label = "1Y", days = "365", cacheTtlMillis = 3_600_000L),
    ALL(label = "ALL", days = "max", cacheTtlMillis = 3_600_000L),
}

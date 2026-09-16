package com.vultisig.wallet.data.models

import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

/**
 * Time ranges offered by the token detail price chart. [days] is CoinGecko's `market_chart` `days`
 * query parameter; [cacheTtl] is how long a fetched series for this range is considered fresh
 * before a re-fetch is attempted (short for 1D, longer for 1Y/ALL, since older data changes less).
 * Display labels live in the `chart_range_*` string resources, not here.
 */
enum class ChartRange(val days: String, val cacheTtl: Duration) {
    ONE_DAY(days = "1", cacheTtl = 1.minutes),
    ONE_WEEK(days = "7", cacheTtl = 10.minutes),
    ONE_MONTH(days = "30", cacheTtl = 10.minutes),
    ONE_YEAR(days = "365", cacheTtl = 1.hours),
    ALL(days = "max", cacheTtl = 1.hours),
}

package com.vultisig.wallet.data.models

import java.math.BigDecimal
import java.time.Instant

/**
 * Market stats, price extremes and supply data for a coin, sourced from CoinGecko's
 * `/coins/markets` endpoint. Every field is nullable because CoinGecko itself omits fields freely
 * (e.g. `max_supply` for uncapped assets) — the UI hides a stat row rather than showing a
 * placeholder when its field is null.
 */
data class CoinMarketStats(
    val marketCap: BigDecimal?,
    val marketCapRank: Int?,
    val fullyDilutedValuation: BigDecimal?,
    val volume24h: BigDecimal?,
    val circulatingSupply: BigDecimal?,
    val maxSupply: BigDecimal?,
    val low24h: BigDecimal?,
    val high24h: BigDecimal?,
    val athPrice: BigDecimal?,
    val athDate: Instant?,
    val athChangePercent: Double?,
    val atlPrice: BigDecimal?,
    val atlDate: Instant?,
    val atlChangePercent: Double?,
)

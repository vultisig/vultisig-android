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
    /**
     * Spot price as of the same `/coins/markets` read that produced [low24h]/[high24h], so the 24h
     * band's marker can never sit outside the band it is drawn in. Deliberately not the account's
     * own price, which is fetched separately and can be minutes apart from these extremes.
     */
    val currentPrice: BigDecimal?,
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
) {

    /**
     * Where [currentPrice] sits inside the 24h band, `0` at the low and `1` at the high. Null when
     * the band is unusable — a missing bound, a missing price, or a degenerate band (CoinGecko
     * reports `high == low` for a coin that hasn't traded).
     */
    fun positionIn24hRange(): Double? {
        val low = low24h ?: return null
        val high = high24h ?: return null
        val price = currentPrice ?: return null
        if (high <= low) return null
        return ((price - low).toDouble() / (high - low).toDouble()).coerceIn(0.0, 1.0)
    }
}

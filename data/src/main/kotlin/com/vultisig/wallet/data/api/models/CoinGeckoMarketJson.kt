package com.vultisig.wallet.data.api.models

import java.math.BigDecimal
import kotlinx.serialization.Contextual
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * CoinGecko `market_chart` response (id and contract variants share this shape). `prices` is an
 * array of `[msEpoch, price]` pairs — decoded as raw `Double` pairs (not `BigDecimal`) since the
 * array-of-arrays shape isn't a natural fit for a per-field `@Contextual` serializer; values are
 * converted to `BigDecimal` in
 * [decodeMarketChartPoints][com.vultisig.wallet.data.utils.decodeMarketChartPoints].
 */
@Serializable data class MarketChartResponseJson(val prices: List<List<Double>> = emptyList())

/**
 * One entry of CoinGecko's `/coins/markets` response. Every numeric field is nullable — CoinGecko
 * omits fields it doesn't have for a given asset (e.g. `max_supply` for an uncapped supply) rather
 * than sending zero.
 */
@Serializable
data class CoinMarketStatsJson(
    @SerialName("current_price") @Contextual val currentPrice: BigDecimal? = null,
    @SerialName("market_cap") @Contextual val marketCap: BigDecimal? = null,
    @SerialName("market_cap_rank") val marketCapRank: Int? = null,
    @SerialName("fully_diluted_valuation")
    @Contextual
    val fullyDilutedValuation: BigDecimal? = null,
    @SerialName("total_volume") @Contextual val totalVolume: BigDecimal? = null,
    @SerialName("high_24h") @Contextual val high24h: BigDecimal? = null,
    @SerialName("low_24h") @Contextual val low24h: BigDecimal? = null,
    @SerialName("circulating_supply") @Contextual val circulatingSupply: BigDecimal? = null,
    @SerialName("max_supply") @Contextual val maxSupply: BigDecimal? = null,
    @SerialName("ath") @Contextual val ath: BigDecimal? = null,
    @SerialName("ath_date") val athDate: String? = null,
    @SerialName("ath_change_percentage") val athChangePercentage: Double? = null,
    @SerialName("atl") @Contextual val atl: BigDecimal? = null,
    @SerialName("atl_date") val atlDate: String? = null,
    @SerialName("atl_change_percentage") val atlChangePercentage: Double? = null,
)

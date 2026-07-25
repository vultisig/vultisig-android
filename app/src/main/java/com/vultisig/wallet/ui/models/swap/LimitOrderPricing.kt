package com.vultisig.wallet.ui.models.swap

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Pure price math for the THORChain limit-order form.
 *
 * The one canonical quantity is **targetPrice = buy-asset units per 1 sell-asset unit** — exactly
 * what the memo's LIM is computed from (`buildLimitSwapMemoForCoins`). Everything the UI shows (the
 * fiat price of one buy unit, the expected buy amount, the % from market, the warnings) is derived
 * from it, so the `$`/asset display toggle can never change the value that gets signed.
 */
internal object LimitOrderPricing {

    /** THORChain's minimum outbound bump: a target price this far above market may never fill. */
    private val FAR_ABOVE_MARKET_RATIO = BigDecimal("1.2")

    private val PRICE_SCALE = 12

    /** Target price for a preset: market price bumped [pctAboveMarket] percent (0 == Market). */
    fun applyPreset(marketTargetPrice: BigDecimal, pctAboveMarket: Int): BigDecimal =
        marketTargetPrice *
            (BigDecimal.ONE +
                BigDecimal(pctAboveMarket)
                    .divide(BigDecimal(100), PRICE_SCALE, RoundingMode.HALF_UP))

    /** Fiat value of one buy unit at [targetPrice], given the sell asset's USD price. */
    fun fiatPricePerBuyUnit(targetPrice: BigDecimal, sellUsdPrice: BigDecimal?): BigDecimal? {
        if (targetPrice.signum() <= 0 || sellUsdPrice == null || sellUsdPrice.signum() <= 0) {
            return null
        }
        val sellPerBuy = BigDecimal.ONE.divide(targetPrice, PRICE_SCALE, RoundingMode.HALF_UP)
        return sellPerBuy * sellUsdPrice
    }

    /** Buy units received for [sellAmount] sell units at [targetPrice]. */
    fun expectedBuyAmount(sellAmount: BigDecimal?, targetPrice: BigDecimal): BigDecimal? {
        if (sellAmount == null || sellAmount.signum() <= 0 || targetPrice.signum() <= 0) return null
        return sellAmount * targetPrice
    }

    /**
     * Signed distance of the target price from market, as a percentage of the displayed (fiat)
     * price. A target that demands a better rate than market (higher canonical [targetPrice]) shows
     * as a cheaper per-buy price, i.e. a negative percentage — matching how the form reads to a
     * user placing a buy order below market.
     */
    fun percentFromMarket(targetPrice: BigDecimal, marketTargetPrice: BigDecimal): BigDecimal? {
        if (targetPrice.signum() <= 0 || marketTargetPrice.signum() <= 0) return null
        val marketOverTarget =
            marketTargetPrice.divide(targetPrice, PRICE_SCALE, RoundingMode.HALF_UP)
        return (marketOverTarget - BigDecimal.ONE) * BigDecimal(100)
    }

    enum class LimitWarning {
        /** targetPrice ≤ market: the order would fill immediately — suggest a market swap. */
        BelowMarket,
        /** targetPrice > market × 1.2: unlikely to fill before expiry. */
        FarAboveMarket,
    }

    fun warningFor(targetPrice: BigDecimal, marketTargetPrice: BigDecimal): LimitWarning? {
        if (targetPrice.signum() <= 0 || marketTargetPrice.signum() <= 0) return null
        return when {
            targetPrice <= marketTargetPrice -> LimitWarning.BelowMarket
            targetPrice > marketTargetPrice * FAR_ABOVE_MARKET_RATIO -> LimitWarning.FarAboveMarket
            else -> null
        }
    }
}

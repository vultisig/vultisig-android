package com.vultisig.wallet.ui.models.swap

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Pure price math for the THORChain limit-order form.
 *
 * The one canonical quantity is **targetPrice = buy-asset units per 1 sell-asset unit** — exactly
 * what the memo's LIM is computed from (`buildLimitSwapMemoForCoins`). Everything the UI shows (the
 * fiat value of one sell unit, the expected buy amount, the % from market, the warnings) is derived
 * from it, so the `$`/asset display toggle can never change the value that gets signed.
 *
 * The form quotes the price **per sell unit** ("1 RUNE is worth 2.6474 DOGE"), matching iOS/macOS,
 * so the rate the user reads is the rate the memo encodes rather than its reciprocal.
 */
internal object LimitOrderPricing {

    /** THORChain's minimum outbound bump: a target price this far above market may never fill. */
    private val FAR_ABOVE_MARKET_RATIO = BigDecimal("1.2")

    private val PRICE_SCALE = 12

    /**
     * Fractional digits the THORChain memo can express for a target price. `LimitSwapMemo` rejects
     * anything longer outright, and the market-price probe divides at scale 18, so every price that
     * can reach [applyPreset] or the signing path is normalized to this grid first.
     */
    private const val MEMO_PRICE_SCALE = 8

    /**
     * Snaps [price] onto the memo's 8-decimal grid.
     *
     * Rounds up, never down: the target price is the user's *minimum* acceptable rate, so rounding
     * down would sign an order fractionally worse than the one displayed, and a price below 1e-8
     * would floor to zero — which THORChain reads as an unprotected market order.
     */
    fun toMemoScale(price: BigDecimal): BigDecimal =
        price.setScale(MEMO_PRICE_SCALE, RoundingMode.CEILING)

    /** Target price for a preset: market price bumped [pctAboveMarket] percent (0 == Market). */
    fun applyPreset(marketTargetPrice: BigDecimal, pctAboveMarket: Int): BigDecimal =
        toMemoScale(
            marketTargetPrice *
                (BigDecimal.ONE +
                    BigDecimal(pctAboveMarket)
                        .divide(BigDecimal(100), PRICE_SCALE, RoundingMode.HALF_UP))
        )

    /**
     * Fiat value of ONE sell unit at [targetPrice] — the figure shown under the "1 <sell>" header.
     *
     * Priced through the BUY asset ([buyUnitFiat] × [targetPrice]) rather than read off the sell
     * asset's own market price, because this is the value the *order* implies, not the value the
     * market currently assigns: raising the preset to +10% must raise this figure by 10%, which a
     * sell-side market price could never do.
     */
    fun fiatPricePerSellUnit(targetPrice: BigDecimal, buyUnitFiat: BigDecimal?): BigDecimal? {
        if (targetPrice.signum() <= 0 || buyUnitFiat == null || buyUnitFiat.signum() <= 0) {
            return null
        }
        return targetPrice * buyUnitFiat
    }

    /** Buy units received for [sellAmount] sell units at [targetPrice]. */
    fun expectedBuyAmount(sellAmount: BigDecimal?, targetPrice: BigDecimal): BigDecimal? {
        if (sellAmount == null || sellAmount.signum() <= 0 || targetPrice.signum() <= 0) return null
        return sellAmount * targetPrice
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

package com.vultisig.wallet.ui.models.swap

import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Locale

/**
 * Qualitative price-impact tier driving the Price Impact row's label and color. Thresholds mirror
 * iOS `SwapCryptoLogic.priceImpactColor` so the label and color always agree.
 */
internal enum class PriceImpactLevel {
    GOOD,
    AVERAGE,
    HIGH,
}

/** Formatted price-impact percentage (e.g. `-1.33%`) and its [PriceImpactLevel]. */
internal data class PriceImpactDisplay(val percent: String, val level: PriceImpactLevel)

// iOS bands (on the negated, display-oriented impact): > -1% Good, > -3% Average, else High.
private val GOOD_THRESHOLD = BigDecimal("-0.01")
private val AVERAGE_THRESHOLD = BigDecimal("-0.03")

/**
 * Formats a fractional price [impact] (e.g. `0.0133` == 1.33%) into a signed percentage string and
 * its quality tier, or null when no impact is available. Mirrors iOS `SwapCryptoLogic`: the node
 * reports a positive slippage, so the value is negated for display (a swap costs the user output),
 * and a leading `+`/`-` makes the direction explicit.
 */
internal fun formatPriceImpact(impact: BigDecimal?): PriceImpactDisplay? {
    if (impact == null) return null
    val displayImpact = impact.negate()
    val percentValue = displayImpact.multiply(BigDecimal(100)).setScale(2, RoundingMode.HALF_UP)
    // "%.2f" already carries the minus sign for negatives; only positives need an explicit "+".
    val sign = if (percentValue.signum() < 0) "" else "+"
    val percent = String.format(Locale.US, "%s%.2f%%", sign, percentValue)
    val level =
        when {
            displayImpact > GOOD_THRESHOLD -> PriceImpactLevel.GOOD
            displayImpact > AVERAGE_THRESHOLD -> PriceImpactLevel.AVERAGE
            else -> PriceImpactLevel.HIGH
        }
    return PriceImpactDisplay(percent, level)
}

package com.vultisig.wallet.ui.models.deposit

import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode

internal object RemoveLpCalculator {

    const val CACAO_DECIMALS = 10
    const val RUNE_DECIMALS = 8
    const val DISPLAY_SCALE = 3
    // Precision kept on `userUnits * poolDepth / totalUnits` before we shift by
    // 10^decimals and round to DISPLAY_SCALE. Must be >= decimals + scale of any call or the
    // first DOWN division discards digits the second one still needs; 18 matches the standard
    // fixed-point scale and leaves headroom over the largest current call (8 + 8).
    const val LP_UNITS_INTERMEDIATE_SCALE = 18

    /**
     * Returns the redeem amount (as a plain string with [DISPLAY_SCALE] decimals) that
     * [selectedUnits] of LP represent in a pool of [totalPoolUnits] total units holding [poolDepth]
     * of the native asset (in fixed-point with [decimals] decimals), or `null` if inputs are
     * invalid (pool not loaded / empty).
     *
     * Rounding is always DOWN so users are never credited more than they are entitled to.
     */
    fun computeAmountDisplay(
        selectedUnits: Long,
        poolDepth: BigInteger,
        totalPoolUnits: BigInteger,
        decimals: Int,
    ): String? =
        computeAmountDisplay(
            selectedUnits = selectedUnits.toBigInteger(),
            poolDepth = poolDepth,
            totalPoolUnits = totalPoolUnits,
            decimals = decimals,
        )

    /**
     * [BigInteger] overload of [computeAmountDisplay] for LP positions whose unit counts exceed
     * `Long.MAX_VALUE` (whale positions). Keeps the redeem-amount math in full-precision
     * fixed-point so no precision is lost converting through `Long`.
     *
     * [scale] is how many decimals the result keeps. It defaults to [DISPLAY_SCALE], which suits
     * RUNE and CACAO; a high-unit-price pool asset needs more, or its whole redeem amount rounds
     * away to `0.000`.
     */
    fun computeAmountDisplay(
        selectedUnits: BigInteger,
        poolDepth: BigInteger,
        totalPoolUnits: BigInteger,
        decimals: Int,
        scale: Int = DISPLAY_SCALE,
    ): String? {
        if (totalPoolUnits.signum() <= 0) return null
        // Both divisions round DOWN, so the intermediate must carry every digit the final scale
        // will read. Below that the result is not merely coarser, it is wrong: at decimals=0 and
        // scale=19 an exact 0.333… would print as 0.3333333333333333330.
        require(decimals + scale <= LP_UNITS_INTERMEDIATE_SCALE) {
            "decimals ($decimals) + scale ($scale) exceeds the intermediate precision " +
                "($LP_UNITS_INTERMEDIATE_SCALE); the result would be silently wrong"
        }
        return selectedUnits
            .toBigDecimal()
            .multiply(poolDepth.toBigDecimal())
            .divide(totalPoolUnits.toBigDecimal(), LP_UNITS_INTERMEDIATE_SCALE, RoundingMode.DOWN)
            .divide(BigDecimal.TEN.pow(decimals), scale, RoundingMode.DOWN)
            .toPlainString()
    }

    /** Drops the padding zeros a fixed [scale] leaves behind: `"1.840"` reads as `"1.84"`. */
    fun trimTrailingZeros(display: String): String =
        display.toBigDecimalOrNull()?.stripTrailingZeros()?.toPlainString() ?: display
}

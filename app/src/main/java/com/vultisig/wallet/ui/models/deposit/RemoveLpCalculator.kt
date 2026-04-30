package com.vultisig.wallet.ui.models.deposit

import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode

internal object RemoveLpCalculator {

    const val CACAO_DECIMALS = 10
    const val RUNE_DECIMALS = 8
    const val DISPLAY_SCALE = 3
    // Precision kept on `userUnits * poolDepth / totalUnits` before we shift by
    // 10^decimals and round to DISPLAY_SCALE. Must be >= max decimals + DISPLAY_SCALE
    // (= 13) to avoid truncation; 18 matches the standard fixed-point scale and leaves
    // headroom.
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
    ): String? {
        if (totalPoolUnits.signum() <= 0) return null
        return selectedUnits
            .toBigDecimal()
            .multiply(poolDepth.toBigDecimal())
            .divide(totalPoolUnits.toBigDecimal(), LP_UNITS_INTERMEDIATE_SCALE, RoundingMode.DOWN)
            .divide(BigDecimal.TEN.pow(decimals), DISPLAY_SCALE, RoundingMode.DOWN)
            .toPlainString()
    }
}

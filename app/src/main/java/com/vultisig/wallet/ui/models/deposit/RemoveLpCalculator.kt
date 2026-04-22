package com.vultisig.wallet.ui.models.deposit

import java.math.BigDecimal
import java.math.RoundingMode

internal object RemoveLpCalculator {

    const val CACAO_DECIMALS = 10
    const val CACAO_DISPLAY_SCALE = 3
    // Precision kept on `userUnits * cacaoDepth / totalUnits` before we shift by
    // 10^CACAO_DECIMALS and round to CACAO_DISPLAY_SCALE. Must be >=
    // CACAO_DECIMALS + CACAO_DISPLAY_SCALE (= 13) to avoid truncation; 18 matches
    // the standard fixed-point scale and leaves headroom.
    const val LP_UNITS_INTERMEDIATE_SCALE = 18

    /**
     * Returns the CACAO amount (as a plain string with [CACAO_DISPLAY_SCALE] decimals) that
     * [selectedUnits] of LP represent in a pool of [totalPoolUnits] total units holding
     * [cacaoDepth] CACAO, or `null` if inputs are invalid (pool not loaded / empty).
     *
     * Rounding is always DOWN so users are never credited more than they are entitled to.
     */
    fun computeCacaoDisplay(selectedUnits: Long, cacaoDepth: Long, totalPoolUnits: Long): String? {
        if (totalPoolUnits <= 0L) return null
        return selectedUnits
            .toBigDecimal()
            .multiply(cacaoDepth.toBigDecimal())
            .divide(totalPoolUnits.toBigDecimal(), LP_UNITS_INTERMEDIATE_SCALE, RoundingMode.DOWN)
            .divide(BigDecimal.TEN.pow(CACAO_DECIMALS), CACAO_DISPLAY_SCALE, RoundingMode.DOWN)
            .toPlainString()
    }
}

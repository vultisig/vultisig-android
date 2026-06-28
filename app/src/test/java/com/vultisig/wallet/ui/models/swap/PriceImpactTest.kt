package com.vultisig.wallet.ui.models.swap

import java.math.BigDecimal
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.jupiter.api.Test

/**
 * Pins [formatPriceImpact], which feeds the swap Price Impact row. The negation, signed formatting
 * and Good/Average/High banding mirror iOS `SwapCryptoLogic` — drift here would show the wrong
 * percentage, sign, or quality label to the user.
 */
internal class PriceImpactTest {

    @Test
    fun `null impact yields no row`() {
        assertNull(formatPriceImpact(null))
    }

    @Test
    fun `small positive slippage is negated and tagged Good`() {
        // 0.50% impact (50 bps) → reads as a -0.50% cost, within the Good band (> -1%).
        val display = formatPriceImpact(BigDecimal("0.005"))!!
        assertEquals("-0.50%", display.percent)
        assertEquals(PriceImpactLevel.GOOD, display.level)
    }

    @Test
    fun `mid slippage is tagged Average`() {
        // 1.33% impact → -1.33%, in the Average band (-1% .. -3%).
        val display = formatPriceImpact(BigDecimal("0.0133"))!!
        assertEquals("-1.33%", display.percent)
        assertEquals(PriceImpactLevel.AVERAGE, display.level)
    }

    @Test
    fun `large slippage is tagged High`() {
        // 5% impact → -5.00%, below the -3% Average floor.
        val display = formatPriceImpact(BigDecimal("0.05"))!!
        assertEquals("-5.00%", display.percent)
        assertEquals(PriceImpactLevel.HIGH, display.level)
    }

    @Test
    fun `1 percent boundary stays Average not Good`() {
        // displayImpact == -0.01 is NOT > -0.01, so the 1% boundary falls into Average.
        val display = formatPriceImpact(BigDecimal("0.01"))!!
        assertEquals("-1.00%", display.percent)
        assertEquals(PriceImpactLevel.AVERAGE, display.level)
    }

    @Test
    fun `3 percent boundary stays High not Average`() {
        // displayImpact == -0.03 is NOT > -0.03, so the 3% boundary falls into High.
        val display = formatPriceImpact(BigDecimal("0.03"))!!
        assertEquals("-3.00%", display.percent)
        assertEquals(PriceImpactLevel.HIGH, display.level)
    }

    @Test
    fun `favorable impact renders an explicit plus sign and Good tier`() {
        // A negative slippage (favorable) negates to a positive display value.
        val display = formatPriceImpact(BigDecimal("-0.002"))!!
        assertEquals("+0.20%", display.percent)
        assertEquals(PriceImpactLevel.GOOD, display.level)
    }
}

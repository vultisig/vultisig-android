package com.vultisig.wallet.ui.models.deposit

import java.math.BigInteger
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.jupiter.api.Test

internal class RemoveLpCalculatorTest {

    @Test
    fun `returns null when pool total units is zero`() {
        val result =
            RemoveLpCalculator.computeAmountDisplay(
                selectedUnits = 1_000L,
                poolDepth = BigInteger.valueOf(10_000_000_000L),
                totalPoolUnits = BigInteger.ZERO,
                decimals = RemoveLpCalculator.CACAO_DECIMALS,
            )
        assertNull(result)
    }

    @Test
    fun `returns null when pool total units is negative`() {
        val result =
            RemoveLpCalculator.computeAmountDisplay(
                selectedUnits = 1_000L,
                poolDepth = BigInteger.valueOf(10_000_000_000L),
                totalPoolUnits = BigInteger.valueOf(-5L),
                decimals = RemoveLpCalculator.CACAO_DECIMALS,
            )
        assertNull(result)
    }

    @Test
    fun `returns zero display value when selected units is zero`() {
        val result =
            RemoveLpCalculator.computeAmountDisplay(
                selectedUnits = 0L,
                poolDepth = BigInteger.valueOf(10_000_000_000L),
                totalPoolUnits = BigInteger.valueOf(1_000L),
                decimals = RemoveLpCalculator.CACAO_DECIMALS,
            )
        // 0 * depth / total, then scaled to 3 decimals
        assertEquals("0.000", result)
    }

    @Test
    fun `computes exact ratio at full withdrawal`() {
        // User owns the whole pool: 1 LP unit of 1 total, depth = 5 CACAO (= 5 * 10^10 base units).
        val result =
            RemoveLpCalculator.computeAmountDisplay(
                selectedUnits = 1L,
                poolDepth = BigInteger.valueOf(50_000_000_000L),
                totalPoolUnits = BigInteger.ONE,
                decimals = RemoveLpCalculator.CACAO_DECIMALS,
            )
        assertEquals("5.000", result)
    }

    @Test
    fun `rounds down on high precision pool division`() {
        // depth = 100 CACAO = 10^12 base units, total = 3, selectedUnits = 1.
        // Share = 10^12 / 3 = 333_333_333_333.333... base units.
        // Display = / 10^10 = 33.3333333333..., rounded DOWN to 3 decimals -> 33.333.
        val result =
            RemoveLpCalculator.computeAmountDisplay(
                selectedUnits = 1L,
                poolDepth = BigInteger.valueOf(1_000_000_000_000L),
                totalPoolUnits = BigInteger.valueOf(3L),
                decimals = RemoveLpCalculator.CACAO_DECIMALS,
            )
        assertEquals("33.333", result)
    }

    @Test
    fun `never rounds up a value just below the next tick`() {
        // depth = 10.0009 CACAO = 100_009_000_000 base units, total = 1, selectedUnits = 1.
        // Display = 10.0009 -> truncated to 3 decimals -> 10.000 (NOT 10.001).
        val result =
            RemoveLpCalculator.computeAmountDisplay(
                selectedUnits = 1L,
                poolDepth = BigInteger.valueOf(100_009_000_000L),
                totalPoolUnits = BigInteger.ONE,
                decimals = RemoveLpCalculator.CACAO_DECIMALS,
            )
        assertEquals("10.000", result)
    }

    @Test
    fun `rune scale produces correct display at full withdrawal`() {
        // RUNE uses 8-decimal fixed-point. Depth = 5 RUNE = 5 * 10^8 base units, total = 1.
        val result =
            RemoveLpCalculator.computeAmountDisplay(
                selectedUnits = 1L,
                poolDepth = BigInteger.valueOf(500_000_000L),
                totalPoolUnits = BigInteger.ONE,
                decimals = RemoveLpCalculator.RUNE_DECIMALS,
            )
        assertEquals("5.000", result)
    }

    @Test
    fun `rune scale halves redemption at half pool ownership`() {
        // RUNE depth = 100 RUNE = 10^10 base units, total = 2 LP units, selectedUnits = 1 -> 50
        // RUNE.
        val result =
            RemoveLpCalculator.computeAmountDisplay(
                selectedUnits = 1L,
                poolDepth = BigInteger.valueOf(10_000_000_000L),
                totalPoolUnits = BigInteger.valueOf(2L),
                decimals = RemoveLpCalculator.RUNE_DECIMALS,
            )
        assertEquals("50.000", result)
    }

    @Test
    fun `handles BigInteger inputs that exceed Long_MAX_VALUE`() {
        // Whale RUNE position: redeem value > Long.MAX_VALUE so Long arithmetic would truncate.
        val poolDepth = BigInteger("100000000000000000000") // 10^20, > Long.MAX_VALUE
        val result =
            RemoveLpCalculator.computeAmountDisplay(
                selectedUnits = 1L,
                poolDepth = poolDepth,
                totalPoolUnits = BigInteger.ONE,
                decimals = RemoveLpCalculator.RUNE_DECIMALS,
            )
        // 10^20 base units / 10^8 = 10^12 RUNE
        assertEquals("1000000000000.000", result)
    }
}

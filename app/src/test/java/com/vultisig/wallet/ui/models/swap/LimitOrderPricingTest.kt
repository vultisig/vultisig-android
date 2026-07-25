package com.vultisig.wallet.ui.models.swap

import java.math.BigDecimal
import java.math.RoundingMode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class LimitOrderPricingTest {

    @Test
    fun `Market preset keeps the market price`() {
        assertEquals(
            0,
            BigDecimal("2.0").compareTo(LimitOrderPricing.applyPreset(BigDecimal("2.0"), 0)),
        )
    }

    @Test
    fun `percentage presets bump the market price`() {
        assertEquals(
            0,
            BigDecimal("1.05").compareTo(LimitOrderPricing.applyPreset(BigDecimal("1.0"), 5)),
        )
        assertEquals(
            0,
            BigDecimal("1.10").compareTo(LimitOrderPricing.applyPreset(BigDecimal("1.0"), 10)),
        )
    }

    @Test
    fun `fiat price of one buy unit inverts the target price and applies the sell USD price`() {
        // target 0.0000152 BTC per USDC -> 1 BTC costs ~65789 USDC -> ~$65789 at $1 USDC.
        val fiat =
            LimitOrderPricing.fiatPricePerBuyUnit(
                targetPrice = BigDecimal("0.0000152"),
                sellUsdPrice = BigDecimal("1"),
            )!!
        assertEquals(0, BigDecimal("65789.47").compareTo(fiat.setScale(2, RoundingMode.HALF_UP)))
    }

    @Test
    fun `fiat price is null without a sell USD price`() {
        assertNull(LimitOrderPricing.fiatPricePerBuyUnit(BigDecimal("2"), null))
    }

    @Test
    fun `expected buy amount multiplies sell amount by target price`() {
        assertEquals(
            0,
            BigDecimal("0.32")
                .compareTo(
                    LimitOrderPricing.expectedBuyAmount(BigDecimal("2"), BigDecimal("0.16"))!!
                ),
        )
    }

    @Test
    fun `percent from market is negative when the target demands a better rate than market`() {
        // target above market (buy per sell) shows as a cheaper displayed price, i.e. negative.
        val pct =
            LimitOrderPricing.percentFromMarket(
                targetPrice = BigDecimal("1.05"),
                marketTargetPrice = BigDecimal("1.0"),
            )!!
        assert(pct.signum() < 0) { "expected negative percent, got $pct" }
    }

    @Test
    fun `warns when the target price is at or below market`() {
        assertEquals(
            LimitOrderPricing.LimitWarning.BelowMarket,
            LimitOrderPricing.warningFor(BigDecimal("1.0"), BigDecimal("1.0")),
        )
        assertEquals(
            LimitOrderPricing.LimitWarning.BelowMarket,
            LimitOrderPricing.warningFor(BigDecimal("0.9"), BigDecimal("1.0")),
        )
    }

    @Test
    fun `warns when the target price is more than 20 percent above market`() {
        assertEquals(
            LimitOrderPricing.LimitWarning.FarAboveMarket,
            LimitOrderPricing.warningFor(BigDecimal("1.25"), BigDecimal("1.0")),
        )
    }

    @Test
    fun `no warning for a target modestly above market`() {
        assertNull(LimitOrderPricing.warningFor(BigDecimal("1.1"), BigDecimal("1.0")))
    }
}

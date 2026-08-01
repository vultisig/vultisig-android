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
    fun `presets stay within the 8 fractional digits the memo accepts`() {
        // The market-price probe divides at scale 18; a preset applied to it must not carry that
        // scale into the memo, which rejects anything longer outright.
        val market = BigDecimal("0.000023456789123456")
        listOf(0, 1, 5, 10).forEach { pct ->
            assertEquals(8, LimitOrderPricing.applyPreset(market, pct).scale())
        }
    }

    @Test
    fun `memo scaling rounds up so the signed price is never worse than shown`() {
        assertEquals(
            BigDecimal("0.00002346"),
            LimitOrderPricing.toMemoScale(BigDecimal("0.000023451")),
        )
    }

    @Test
    fun `memo scaling never floors a positive price to zero`() {
        assertEquals(
            BigDecimal("0.00000001"),
            LimitOrderPricing.toMemoScale(BigDecimal("0.000000000001")),
        )
    }

    @Test
    fun `Market preset still reads as at-market after scaling`() {
        val market = LimitOrderPricing.toMemoScale(BigDecimal("0.000023456789123456"))
        assertEquals(
            LimitOrderPricing.LimitWarning.BelowMarket,
            LimitOrderPricing.warningFor(LimitOrderPricing.applyPreset(market, 0), market),
        )
    }

    @Test
    fun `fiat price of one sell unit prices the target price through the buy asset`() {
        // target 2.6474 DOGE per RUNE at $0.16 a DOGE -> 1 RUNE is worth ~$0.42.
        val fiat =
            LimitOrderPricing.fiatPricePerSellUnit(
                targetPrice = BigDecimal("2.6474"),
                buyUnitFiat = BigDecimal("0.16"),
            )!!
        assertEquals(0, BigDecimal("0.42").compareTo(fiat.setScale(2, RoundingMode.HALF_UP)))
    }

    @Test
    fun `raising the preset raises the fiat value of one sell unit by the same percent`() {
        // The header figure has to track the ORDER, not the market: a +10% target must read 10%
        // higher. Reading it off the sell asset's own market price would leave it unchanged.
        val market = BigDecimal("2.6474")
        val atMarket = LimitOrderPricing.fiatPricePerSellUnit(market, BigDecimal("0.16"))!!
        val plusTen =
            LimitOrderPricing.fiatPricePerSellUnit(
                LimitOrderPricing.applyPreset(market, 10),
                BigDecimal("0.16"),
            )!!
        assertEquals(
            0,
            (atMarket * BigDecimal("1.1"))
                .setScale(6, RoundingMode.HALF_UP)
                .compareTo(plusTen.setScale(6, RoundingMode.HALF_UP)),
        )
    }

    @Test
    fun `fiat price is null without a buy unit price`() {
        assertNull(LimitOrderPricing.fiatPricePerSellUnit(BigDecimal("2"), null))
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

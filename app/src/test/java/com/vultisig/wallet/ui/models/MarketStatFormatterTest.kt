package com.vultisig.wallet.ui.models

import io.kotest.matchers.shouldBe
import java.math.BigDecimal
import java.util.Locale
import org.junit.jupiter.api.Test

internal class MarketStatFormatterTest {

    private val locale = Locale.US

    @Test
    fun `figures at or above a million are abbreviated, smaller ones are not`() {
        MarketStatFormatter.isAbbreviated(BigDecimal("999999")) shouldBe false
        MarketStatFormatter.isAbbreviated(BigDecimal("1000000")) shouldBe true
    }

    @Test
    fun `each magnitude gets its own suffix`() {
        MarketStatFormatter.abbreviate(BigDecimal("6960000"), locale) shouldBe "6.96M"
        MarketStatFormatter.abbreviate(BigDecimal("1280000000"), locale) shouldBe "1.28B"
        MarketStatFormatter.abbreviate(BigDecimal("2226290000000"), locale) shouldBe "2.22T"
    }

    @Test
    fun `abbreviation truncates rather than rounds up`() {
        // 2.22629T rounded to 2 places would read 2.23T — larger than the value it stands for.
        MarketStatFormatter.abbreviate(BigDecimal("2226290000000"), locale) shouldBe "2.22T"
        MarketStatFormatter.abbreviate(BigDecimal("1999999999"), locale) shouldBe "1.99B"
    }

    @Test
    fun `a whole magnitude drops its empty fraction`() {
        MarketStatFormatter.abbreviate(BigDecimal("2000000"), locale) shouldBe "2M"
    }

    @Test
    fun `a negative figure keeps its sign`() {
        MarketStatFormatter.abbreviate(BigDecimal("-1500000"), locale) shouldBe "-1.5M"
    }

    @Test
    fun `supply carries the ticker and is abbreviated above the threshold`() {
        MarketStatFormatter.supply(BigDecimal("120680000"), "ETH", locale) shouldBe "120.68M ETH"
        MarketStatFormatter.supply(BigDecimal("21000"), "BTC", locale) shouldBe "21,000 BTC"
    }

    @Test
    fun `a supply CoinGecko does not track has no row`() {
        MarketStatFormatter.supply(BigDecimal.ZERO, "BTC", locale) shouldBe null
        MarketStatFormatter.supply(BigDecimal("-1"), "BTC", locale) shouldBe null
    }

    @Test
    fun `percentages are signed to two places in both directions`() {
        MarketStatFormatter.percent(-62.1637, locale) shouldBe "-62.16%"
        MarketStatFormatter.percent(23.164, locale) shouldBe "+23.16%"
        MarketStatFormatter.percent(0.0, locale) shouldBe "+0.00%"
    }

    @Test
    fun `an unknown currency code falls back to the locale's symbol instead of throwing`() {
        MarketStatFormatter.currencySymbol("USD", locale) shouldBe "$"
        MarketStatFormatter.currencySymbol("NOTACURRENCY", locale) shouldBe "$"
    }
}

package com.vultisig.wallet.ui.screens.v2.defi

import io.kotest.matchers.shouldBe
import java.math.BigInteger
import java.util.Locale
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test

internal class DeFiFormattersTest {

    private val defaultLocale = Locale.getDefault()

    @AfterEach
    fun tearDown() {
        Locale.setDefault(defaultLocale)
    }

    @Test
    fun `an amount is grouped and separated the way the locale writes numbers`() {
        Locale.setDefault(Locale.US)
        BigInteger("123456789012").formatAmount(decimals = 8, symbol = "RUNE") shouldBe
            "1,234.56789012 RUNE"

        Locale.setDefault(Locale.GERMANY)
        BigInteger("123456789012").formatAmount(decimals = 8, symbol = "RUNE") shouldBe
            "1.234,56789012 RUNE"
    }

    @Test
    fun `an empty position reads zero in the locale's own separator`() {
        // The zero branch returns early, so it needs the formatter too — otherwise a German user
        // reads "0.0 RUNE" on an empty position and "1.234,5 RUNE" on a funded one.
        Locale.setDefault(Locale.US)
        BigInteger.ZERO.formatAmount(decimals = 8, symbol = "RUNE") shouldBe "0.0 RUNE"

        Locale.setDefault(Locale.GERMANY)
        BigInteger.ZERO.formatAmount(decimals = 8, symbol = "RUNE") shouldBe "0,0 RUNE"
    }

    @Test
    fun `a percentage follows the locale and is no longer pinned to en-US`() {
        Locale.setDefault(Locale.US)
        0.1327.formatPercentage() shouldBe "13.27%"

        Locale.setDefault(Locale.GERMANY)
        0.1327.formatPercentage() shouldBe "13,27%"
    }

    @Test
    fun `a non-finite APY renders rather than throwing`() {
        // BigDecimal cannot hold either, and an APY read off the wire can be both.
        Double.NaN.formatPercentage() shouldBe "NaN%"
        Double.POSITIVE_INFINITY.formatPercentage() shouldBe "Infinity%"
    }
}

package com.vultisig.wallet.ui.utils

import io.kotest.matchers.shouldBe
import java.math.BigDecimal
import java.util.Locale
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test

internal class TokenAmountFormatTest {

    private val defaultLocale = Locale.getDefault()

    @AfterEach
    fun tearDown() {
        Locale.setDefault(defaultLocale)
    }

    @Test
    fun `a dot locale keeps the English rendering`() {
        BigDecimal("1054.427822").formatTokenAmount("USDC", locale = Locale.US) shouldBe
            "1,054.427822 USDC"
    }

    @Test
    fun `a comma locale writes the decimal separator its own way`() {
        BigDecimal("1054.427822").formatTokenAmount("USDC", locale = Locale.GERMANY) shouldBe
            "1.054,427822 USDC"
        // Russian groups with a non-breaking space, which is exactly why the separator cannot be
        // hardcoded.
        BigDecimal("1054.427822")
            .formatTokenAmount("USDC", locale = Locale.forLanguageTag("ru-RU")) shouldBe
            "1 054,427822 USDC"
    }

    @Test
    fun `the receiver's scale is the display precision`() {
        // A caller that rounded to four places keeps four, trailing zeros included.
        BigDecimal("12.3400").formatTokenAmount("CACAO", locale = Locale.US) shouldBe
            "12.3400 CACAO"
        // A caller that stripped them keeps none.
        BigDecimal("12.3400")
            .stripTrailingZeros()
            .formatTokenAmount("CACAO", locale = Locale.US) shouldBe "12.34 CACAO"
        // stripTrailingZeros leaves whole numbers at a negative scale; that is not fewer digits.
        BigDecimal("800.000000")
            .stripTrailingZeros()
            .formatTokenAmount("TRX", locale = Locale.US) shouldBe "800 TRX"
    }

    @Test
    fun `an explicit precision truncates rather than rounds up`() {
        // Rounding up would claim more than the position holds.
        BigDecimal("1.99999").formatTokenAmount("SOL", decimals = 4, locale = Locale.US) shouldBe
            "1.9999 SOL"
    }

    @Test
    fun `an omitted or blank ticker leaves the number bare`() {
        BigDecimal("1000.5").formatTokenAmount(locale = Locale.US) shouldBe "1,000.5"
        BigDecimal("1000.5").formatTokenAmount("", locale = Locale.US) shouldBe "1,000.5"
    }

    @Test
    fun `percentages follow the same locale separators`() {
        BigDecimal("4.00").formatPercent(locale = Locale.US) shouldBe "4.00%"
        BigDecimal("4.00").formatPercent(locale = Locale.GERMANY) shouldBe "4,00%"
        BigDecimal("13.27").formatPercent(decimals = 2, locale = Locale.US) shouldBe "13.27%"
    }

    @Test
    fun `the default locale is the user's locale`() {
        Locale.setDefault(Locale.GERMANY)

        BigDecimal("1054.43").formatTokenAmount("USDC") shouldBe "1.054,43 USDC"
        BigDecimal("4.00").formatPercent() shouldBe "4,00%"
    }
}

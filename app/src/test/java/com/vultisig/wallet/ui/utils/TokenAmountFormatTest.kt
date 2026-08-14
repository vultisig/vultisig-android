package com.vultisig.wallet.ui.utils

import java.math.BigDecimal
import java.util.Locale
import kotlin.test.assertEquals
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
        assertEquals(
            "1,054.427822 USDC",
            BigDecimal("1054.427822").formatTokenAmount("USDC", locale = Locale.US),
        )
    }

    @Test
    fun `a comma locale writes the decimal separator its own way`() {
        assertEquals(
            "1.054,427822 USDC",
            BigDecimal("1054.427822").formatTokenAmount("USDC", locale = Locale.GERMANY),
        )
        // Russian groups with a non-breaking space, which is exactly why the separator cannot be
        // hardcoded.
        assertEquals(
            "1 054,427822 USDC",
            BigDecimal("1054.427822")
                .formatTokenAmount("USDC", locale = Locale.forLanguageTag("ru-RU")),
        )
    }

    @Test
    fun `the receiver's scale is the display precision`() {
        // A caller that rounded to four places keeps four, trailing zeros included.
        assertEquals(
            "12.3400 CACAO",
            BigDecimal("12.3400").formatTokenAmount("CACAO", locale = Locale.US),
        )
        // A caller that stripped them keeps none.
        assertEquals(
            "12.34 CACAO",
            BigDecimal("12.3400")
                .stripTrailingZeros()
                .formatTokenAmount("CACAO", locale = Locale.US),
        )
        // stripTrailingZeros leaves whole numbers at a negative scale; that is not fewer digits.
        assertEquals(
            "800 TRX",
            BigDecimal("800.000000")
                .stripTrailingZeros()
                .formatTokenAmount("TRX", locale = Locale.US),
        )
    }

    @Test
    fun `an explicit precision truncates rather than rounds up`() {
        // Rounding up would claim more than the position holds.
        assertEquals(
            "1.9999 SOL",
            BigDecimal("1.99999").formatTokenAmount("SOL", decimals = 4, locale = Locale.US),
        )
    }

    @Test
    fun `an omitted or blank ticker leaves the number bare`() {
        assertEquals("1,000.5", BigDecimal("1000.5").formatTokenAmount(locale = Locale.US))
        assertEquals("1,000.5", BigDecimal("1000.5").formatTokenAmount("", locale = Locale.US))
    }

    @Test
    fun `percentages follow the same locale separators`() {
        assertEquals("4.00%", BigDecimal("4.00").formatPercent(locale = Locale.US))
        assertEquals("4,00%", BigDecimal("4.00").formatPercent(locale = Locale.GERMANY))
        assertEquals("13.27%", BigDecimal("13.27").formatPercent(decimals = 2, locale = Locale.US))
    }

    @Test
    fun `the default locale is the user's locale`() {
        Locale.setDefault(Locale.GERMANY)

        assertEquals("1.054,43 USDC", BigDecimal("1054.43").formatTokenAmount("USDC"))
        assertEquals("4,00%", BigDecimal("4.00").formatPercent())
    }
}

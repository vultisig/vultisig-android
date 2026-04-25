package com.vultisig.wallet.data.utils

import com.vultisig.wallet.data.models.settings.AppCurrency
import java.math.BigDecimal
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class AppCurrencyScalingTest {

    @Test
    fun `JPY trims fiat to whole units`() {
        assertEquals(BigDecimal("1234"), BigDecimal("1234.567").scaledFor(AppCurrency.JPY))
        assertEquals(0, AppCurrency.JPY.fractionDigits)
    }

    @Test
    fun `USD keeps two fraction digits`() {
        assertEquals(BigDecimal("1234.56"), BigDecimal("1234.567").scaledFor(AppCurrency.USD))
        assertEquals(2, AppCurrency.USD.fractionDigits)
    }

    @Test
    fun `EUR keeps two fraction digits`() {
        assertEquals(BigDecimal("0.99"), BigDecimal("0.999").scaledFor(AppCurrency.EUR))
    }

    @Test
    fun `RUB keeps two fraction digits`() {
        assertEquals(BigDecimal("12.34"), BigDecimal("12.349").scaledFor(AppCurrency.RUB))
    }

    @Test
    fun `rounding mode is DOWN`() {
        assertEquals(BigDecimal("1.99"), BigDecimal("1.999").scaledFor(AppCurrency.USD))
        assertEquals(BigDecimal("0.00"), BigDecimal("0.005").scaledFor(AppCurrency.USD))
    }

    @Test
    fun `every supported currency reports a non-negative scale`() {
        AppCurrency.entries.forEach { currency ->
            val digits = currency.fractionDigits
            assert(digits >= 0) { "${currency.ticker} reported $digits" }
        }
    }
}

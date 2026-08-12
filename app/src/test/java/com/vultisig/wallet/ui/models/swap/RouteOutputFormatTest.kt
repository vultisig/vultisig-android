package com.vultisig.wallet.ui.models.swap

import java.math.BigDecimal
import kotlin.test.assertEquals
import org.junit.jupiter.api.Test

/**
 * The Select-route output column. It compares routes digit by digit, so it keeps full precision
 * where that is readable — but the column carries no layout weight while the provider name does, so
 * an unbounded amount would squeeze the name out of the row entirely. iOS draws the same line at a
 * million ([`Decimal.formatForDisplay`]).
 */
internal class RouteOutputFormatTest {

    @Test
    fun `keeps every quoted digit below a million`() {
        // The whole point of the column: two routes that differ in the eighth decimal must still
        // read as different rows.
        assertEquals("21.83561311", formatRouteOutput(BigDecimal("21.83561311")))
        assertEquals("21.83561312", formatRouteOutput(BigDecimal("21.83561312")))
    }

    @Test
    fun `groups thousands and drops trailing zeros`() {
        assertEquals("999,999.5", formatRouteOutput(BigDecimal("999999.50000000")))
        assertEquals("1,234", formatRouteOutput(BigDecimal("1234")))
    }

    @Test
    fun `abbreviates from a million up so the row stays inside its budget`() {
        // The unabbreviated form ("375,000,000.12345678") measures wider than the row's whole
        // two-column budget at 360dp and leaves the provider name nothing to render in.
        assertEquals("375M", formatRouteOutput(BigDecimal("375000000.12345678")))
        assertEquals("1M", formatRouteOutput(BigDecimal("1000000")))
        assertEquals("1.23M", formatRouteOutput(BigDecimal("1234567")))
        assertEquals("2.5B", formatRouteOutput(BigDecimal("2500000000")))
        assertEquals("1.5T", formatRouteOutput(BigDecimal("1500000000000")))
    }

    @Test
    fun `truncates rather than rounding up`() {
        // A row must never advertise more output than the quote actually pays.
        assertEquals("1.99M", formatRouteOutput(BigDecimal("1999999")))
        assertEquals("0.99999999", formatRouteOutput(BigDecimal("0.999999999")))
    }

    @Test
    fun `just below the abbreviation threshold stays exact`() {
        assertEquals("999,999.99999999", formatRouteOutput(BigDecimal("999999.99999999")))
    }
}

package com.vultisig.wallet.ui.models.swap

import com.vultisig.wallet.data.models.TokenValue
import java.math.BigDecimal
import java.math.BigInteger
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class FormatFlippedAmountTest {

    @Test
    fun `formatFullAmount keeps full precision for tokens with more than 8 decimals`() {
        // POPCAT has 9 decimals; balance of 404408822 base units (0.404408822).
        val decimals = 9
        val balanceBaseUnits = BigInteger("404408822")

        val maxAmount =
            TokenValue.createDecimal(balanceBaseUnits, decimals)
                .multiply(BigDecimal.ONE)
                .formatFullAmount(decimals)

        val swappedBaseUnits =
            BigDecimal(maxAmount).multiply(BigDecimal.TEN.pow(decimals)).toBigInteger()

        assertEquals("0.404408822", maxAmount)
        assertEquals(BigInteger.ZERO, balanceBaseUnits - swappedBaseUnits)
    }

    @Test
    fun `formatFlippedAmount would leave dust for tokens with more than 8 decimals`() {
        // The old display-capped path truncates the 9th decimal, leaving 2 base units behind.
        val decimals = 9
        val balanceBaseUnits = BigInteger("404408822")

        val cappedAmount =
            TokenValue.createDecimal(balanceBaseUnits, decimals)
                .multiply(BigDecimal.ONE)
                .formatFlippedAmount(decimals)

        val swappedBaseUnits =
            BigDecimal(cappedAmount).multiply(BigDecimal.TEN.pow(decimals)).toBigInteger()

        assertEquals(BigInteger("2"), balanceBaseUnits - swappedBaseUnits)
    }

    @Test
    fun `truncates to token decimals when less than max`() {
        assertEquals("1.123456", BigDecimal("1.123456789012").formatFlippedAmount(6))
    }

    @Test
    fun `truncates to max display decimals when token has more`() {
        assertEquals("0.12345678", BigDecimal("0.12345678901234567890").formatFlippedAmount(18))
    }

    @Test
    fun `strips trailing zeros`() {
        assertEquals("5.1", BigDecimal("5.100000").formatFlippedAmount(8))
    }

    @Test
    fun `handles whole number`() {
        assertEquals("42", BigDecimal("42.000000").formatFlippedAmount(8))
    }

    @Test
    fun `uses max display decimals when token decimals is null`() {
        assertEquals("0.12345678", BigDecimal("0.123456789012").formatFlippedAmount())
    }

    @Test
    fun `truncates down not rounds up`() {
        assertEquals("1.999999", BigDecimal("1.999999999").formatFlippedAmount(6))
    }

    @Test
    fun `handles very small amount`() {
        assertEquals("0.00000001", BigDecimal("0.00000001").formatFlippedAmount(8))
    }

    @Test
    fun `handles zero`() {
        assertEquals("0", BigDecimal.ZERO.formatFlippedAmount(8))
    }
}

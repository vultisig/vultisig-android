package com.vultisig.wallet.ui.models.swap

import com.vultisig.wallet.data.models.TokenValue
import java.math.BigDecimal
import java.math.BigInteger
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class FormatExactAmountTest {

    @Test
    fun `keeps full precision for eighteen decimal token`() {
        assertEquals(
            "5.987654321098765432",
            BigDecimal("5.987654321098765432").formatExactAmount(18),
        )
    }

    @Test
    fun `max tap on eighteen decimal token strands no dust`() {
        val balance = TokenValue(BigInteger("5987654321098765432"), "TKN", 18)

        assertEquals(
            "5.987654321098765432",
            balance.decimal.multiply(1f.toBigDecimal()).formatExactAmount(balance.decimals),
        )
    }

    @Test
    fun `percentage tap truncates extra digits to token decimals`() {
        val balance = TokenValue(BigInteger("5987654321098765432"), "TKN", 18)

        assertEquals(
            "1.496913580274691358",
            balance.decimal.multiply(0.25f.toBigDecimal()).formatExactAmount(balance.decimals),
        )
    }

    @Test
    fun `six decimal token is unchanged`() {
        val balance = TokenValue(BigInteger("1234567"), "USDT", 6)

        assertEquals(
            balance.decimal.formatFlippedAmount(balance.decimals),
            balance.decimal.formatExactAmount(balance.decimals),
        )
    }

    @Test
    fun `truncates down not rounds up`() {
        assertEquals("1.999999999", BigDecimal("1.9999999999").formatExactAmount(9))
    }

    @Test
    fun `strips trailing zeros`() {
        assertEquals("5.1", BigDecimal("5.100000000000").formatExactAmount(18))
    }

    @Test
    fun `handles zero`() {
        assertEquals("0", BigDecimal.ZERO.formatExactAmount(18))
    }
}

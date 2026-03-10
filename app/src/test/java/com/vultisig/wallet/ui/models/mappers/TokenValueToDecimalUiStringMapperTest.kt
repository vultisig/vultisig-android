package com.vultisig.wallet.ui.models.mappers

import com.vultisig.wallet.data.models.TokenValue
import java.math.BigInteger
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class TokenValueToDecimalUiStringMapperTest {

    private val mapTokenValueToDecimalUiString = TokenValueToDecimalUiStringMapperImpl()

    @Test
    fun `test decimal greater than or equal to ONE_BILLION`() {
        val tokenValue = TokenValue(BigInteger.valueOf(3_881_138_658_686), "TOKEN", 2)
        val result = mapTokenValueToDecimalUiString(tokenValue)
        assertEquals("38.8B", result)
    }

    @Test
    fun `test decimal greater than or equal to ONE_MILLION but less than ONE_BILLION`() {
        val tokenValue = TokenValue(BigInteger.valueOf(101_000_000), "TOKEN", 1)
        val result = mapTokenValueToDecimalUiString(tokenValue)
        assertEquals("10.1M", result)
    }

    @Test
    fun `test decimal less than ONE_MILLION`() {
        val tokenValue = TokenValue(BigInteger.valueOf(123_456), "TOKEN", 2)
        val result = mapTokenValueToDecimalUiString(tokenValue)
        assertEquals("1,234.56", result)
    }

    @Test
    fun `test decimal with zero`() {
        val tokenValue = TokenValue(BigInteger.valueOf(2), "TOKEN", 0)
        val result = mapTokenValueToDecimalUiString(tokenValue)
        assertEquals("2", result)
    }
}

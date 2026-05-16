@file:OptIn(ExperimentalCoroutinesApi::class)

package com.vultisig.wallet.ui.models.mappers

import com.vultisig.wallet.data.models.FiatValue
import com.vultisig.wallet.data.repositories.AppCurrencyRepository
import io.mockk.coEvery
import io.mockk.mockk
import java.math.BigDecimal
import java.text.NumberFormat
import java.util.Locale
import kotlin.test.assertEquals
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

internal class FiatValueToStringMapperImplTest {

    private val appCurrencyRepository =
        mockk<AppCurrencyRepository>().also {
            coEvery { it.getCurrencyFormat() } answers
                {
                    NumberFormat.getCurrencyInstance(Locale.US)
                }
        }

    private val mapper = FiatValueToStringMapperImpl(appCurrencyRepository)

    @Test
    fun `forFee renders sub-cent value with 4 fraction digits`() = runTest {
        val result = mapper.forFee(FiatValue(BigDecimal("0.0015"), "USD"))
        assertEquals("$0.0015", result)
    }

    @Test
    fun `forFee renders very small sub-cent value with 4 fraction digits`() = runTest {
        val result = mapper.forFee(FiatValue(BigDecimal("0.0001"), "USD"))
        assertEquals("$0.0001", result)
    }

    @Test
    fun `forFee renders 0_0099 with 4 fraction digits`() = runTest {
        val result = mapper.forFee(FiatValue(BigDecimal("0.0099"), "USD"))
        assertEquals("$0.0099", result)
    }

    @Test
    fun `forFee falls back to standard formatting at exactly 0_01`() = runTest {
        val value = FiatValue(BigDecimal("0.01"), "USD")
        assertEquals(mapper.invoke(value), mapper.forFee(value))
        assertEquals("$0.01", mapper.forFee(value))
    }

    @Test
    fun `forFee falls back to standard formatting for zero`() = runTest {
        val value = FiatValue(BigDecimal.ZERO, "USD")
        assertEquals(mapper.invoke(value), mapper.forFee(value))
    }

    @Test
    fun `forFee falls back to standard formatting for negative value`() = runTest {
        val value = FiatValue(BigDecimal("-0.005"), "USD")
        assertEquals(mapper.invoke(value), mapper.forFee(value))
    }
}

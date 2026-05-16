@file:OptIn(ExperimentalCoroutinesApi::class)

package com.vultisig.wallet.ui.models.mappers

import com.vultisig.wallet.data.models.FiatValue
import com.vultisig.wallet.data.repositories.AppCurrencyRepository
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.mockk.coEvery
import io.mockk.mockk
import java.math.BigDecimal
import java.text.NumberFormat
import java.util.Locale
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
    fun `asFee renders sub-cent USD value with 4 fraction digits`() = runTest {
        val result = mapper(FiatValue(BigDecimal("0.0015"), "USD"), asFee = true)
        result shouldBe "$0.0015"
    }

    @Test
    fun `asFee truncates sub-cent USD value using RoundingMode_DOWN`() = runTest {
        // 7 fraction digits → truncated (not rounded up) to 5: 0.0001256 → 0.00012.
        val result = mapper(FiatValue(BigDecimal("0.0001256"), "USD"), asFee = true)
        result shouldBe "$0.00012"
    }

    @Test
    fun `asFee renders very small sub-cent USD value with up to 5 fraction digits`() = runTest {
        val result = mapper(FiatValue(BigDecimal("0.00001"), "USD"), asFee = true)
        result shouldBe "$0.00001"
    }

    @Test
    fun `asFee renders sub-cent USD value just below threshold`() = runTest {
        val result = mapper(FiatValue(BigDecimal("0.0099"), "USD"), asFee = true)
        result shouldBe "$0.0099"
    }

    @Test
    fun `asFee falls back to standard formatting at exactly 0_01 USD`() = runTest {
        val value = FiatValue(BigDecimal("0.01"), "USD")
        mapper(value, asFee = true) shouldBe mapper(value)
        mapper(value, asFee = true) shouldBe "$0.01"
    }

    @Test
    fun `asFee falls back to standard formatting for zero`() = runTest {
        val value = FiatValue(BigDecimal.ZERO, "USD")
        mapper(value, asFee = true) shouldBe mapper(value)
    }

    @Test
    fun `asFee falls back to standard formatting for negative value`() = runTest {
        val value = FiatValue(BigDecimal("-0.005"), "USD")
        mapper(value, asFee = true) shouldBe mapper(value)
    }

    @Test
    fun `asFee scales threshold for zero-decimal currency JPY`() = runTest {
        // JPY default fraction digits = 0, so fee mode activates only below 1.
        mapper(FiatValue(BigDecimal("0.5"), "JPY"), asFee = true) shouldContain "0.5"

        // At 1 JPY: falls through to standard formatting (0 fraction digits).
        val whole = mapper(FiatValue(BigDecimal("1"), "JPY"), asFee = true)
        whole shouldBe mapper(FiatValue(BigDecimal("1"), "JPY"))
    }

    @Test
    fun `asFee scales threshold for three-decimal currency KWD`() = runTest {
        // KWD default fraction digits = 3, fee mode extends to up to 6 fractional digits.
        mapper(FiatValue(BigDecimal("0.000125"), "KWD"), asFee = true) shouldContain "0.000125"
    }

    @Test
    fun `invoke without asFee uses standard currency formatting`() = runTest {
        // Sub-cent value with standard formatting truncates to USD's 2 digits.
        mapper(FiatValue(BigDecimal("0.0015"), "USD")) shouldBe "$0.00"
    }
}

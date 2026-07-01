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

    @Test
    fun `asPrice reveals a sub-cent token price instead of collapsing to zero`() = runTest {
        // LUNC-like price: standard formatting shows $0.00, asPrice reveals the leading figures.
        // Four leading zeros collapse into subscript notation.
        mapper(FiatValue(BigDecimal("0.00006"), "USD"), asPrice = true) shouldBe "$0.0₄6"
    }

    @Test
    fun `asPrice keeps up to four significant figures of a sub-cent price`() = runTest {
        mapper(FiatValue(BigDecimal("0.00006123"), "USD"), asPrice = true) shouldBe "$0.0₄6123"
    }

    @Test
    fun `asPrice rounds HALF_UP to four significant figures`() = runTest {
        mapper(FiatValue(BigDecimal("0.000061237"), "USD"), asPrice = true) shouldBe "$0.0₄6124"
    }

    @Test
    fun `asPrice renders a micro price with the leading-zero count as a subscript`() = runTest {
        // 0.000000123 -> six leading zeros -> $0.0₆123.
        mapper(FiatValue(BigDecimal("0.000000123"), "USD"), asPrice = true) shouldBe "$0.0₆123"
    }

    @Test
    fun `asPrice uses subscript notation for extremely tiny prices`() = runTest {
        // Parity with desktop: 0.00000003 -> seven leading zeros -> $0.0₇3.
        mapper(FiatValue(BigDecimal("0.00000003"), "USD"), asPrice = true) shouldBe "$0.0₇3"
    }

    @Test
    fun `asPrice collapses four leading zeros while keeping the significant digits`() = runTest {
        mapper(FiatValue(BigDecimal("0.00001234"), "USD"), asPrice = true) shouldBe "$0.0₄1234"
    }

    @Test
    fun `asPrice keeps plain decimals below the subscript threshold`() = runTest {
        // Three leading zeros stay as plain decimals (no subscript).
        mapper(FiatValue(BigDecimal("0.0001234"), "USD"), asPrice = true) shouldBe "$0.0001234"
        mapper(FiatValue(BigDecimal("0.00456"), "USD"), asPrice = true) shouldBe "$0.00456"
    }

    @Test
    fun `asPrice shifts the leading-zero count when rounding carries`() = runTest {
        // 0.0000999999 rounds up to 0.0001, dropping from four to three leading zeros -> plain.
        mapper(FiatValue(BigDecimal("0.0000999999"), "USD"), asPrice = true) shouldBe "$0.0001"
    }

    @Test
    fun `asPrice falls back to standard formatting at exactly one cent`() = runTest {
        // The subUnit guard is exclusive, so 0.01 is the first value that must use standard format.
        val value = FiatValue(BigDecimal("0.01"), "USD")
        mapper(value, asPrice = true) shouldBe mapper(value)
        mapper(value, asPrice = true) shouldBe "$0.01"
    }

    @Test
    fun `asPrice falls back to standard formatting above one cent`() = runTest {
        val value = FiatValue(BigDecimal("1.50"), "USD")
        mapper(value, asPrice = true) shouldBe mapper(value)
        mapper(value, asPrice = true) shouldBe "$1.50"
    }

    @Test
    fun `asPrice falls back to standard formatting for zero`() = runTest {
        val value = FiatValue(BigDecimal.ZERO, "USD")
        mapper(value, asPrice = true) shouldBe mapper(value)
    }

    @Test
    fun `asFee takes precedence over asPrice`() = runTest {
        val value = FiatValue(BigDecimal("0.0001256"), "USD")
        mapper(value, asFee = true, asPrice = true) shouldBe mapper(value, asFee = true)
    }
}

package com.vultisig.wallet.ui.models.mappers

import com.vultisig.wallet.data.models.FiatValue
import com.vultisig.wallet.data.repositories.AppCurrencyRepository
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import java.text.AttributedCharacterIterator
import java.text.CharacterIterator
import java.text.NumberFormat
import java.util.Currency
import javax.inject.Inject

internal interface FiatValueToStringMapper {
    /**
     * Formats a fiat value for display.
     *
     * When [asFee] is true and the value lies between zero and one standard sub-unit of the
     * currency (e.g. less than `0.01` for USD, less than `1` for JPY), the result is rendered with
     * extra precision (up to three extra fraction digits) using [RoundingMode.DOWN] so a sub-unit
     * fee like `$0.001234` displays as `$0.00123` instead of being truncated to `$0.00`.
     *
     * When [asPrice] is true and the value is a sub-unit price, the result is rendered with a few
     * significant figures instead of collapsing to `$0.00`. Once there are four or more leading
     * zeros after the decimal point, they are collapsed into compact subscript notation matching
     * the desktop/extension contract, so `0.00000003` displays as `$0.0₇3` ("seven zeros then 3")
     * while shallower prices like `0.0001234` keep their plain decimals as `$0.0001234`.
     *
     * All other values fall through to the currency's standard formatting. [asFee] takes precedence
     * over [asPrice] when both are set.
     */
    suspend operator fun invoke(
        value: FiatValue,
        asFee: Boolean = false,
        asPrice: Boolean = false,
    ): String
}

internal class FiatValueToStringMapperImpl
@Inject
constructor(private val appCurrencyRepository: AppCurrencyRepository) : FiatValueToStringMapper {

    override suspend fun invoke(value: FiatValue, asFee: Boolean, asPrice: Boolean): String {
        val currency = Currency.getInstance(value.currency)
        val format =
            (appCurrencyRepository.getCurrencyFormat().clone() as NumberFormat).apply {
                this.currency = currency
            }
        if (!asFee && !asPrice) {
            return format.format(value.value)
        }
        val standardDigits = currency.defaultFractionDigits.coerceAtLeast(0)
        val subUnit = BigDecimal.ONE.movePointLeft(standardDigits)
        if (value.value <= BigDecimal.ZERO || value.value >= subUnit) {
            return format.format(value.value)
        }
        if (asFee) {
            format.minimumFractionDigits = standardDigits
            format.maximumFractionDigits = standardDigits + EXTRA_FEE_PRECISION_DIGITS
            format.roundingMode = RoundingMode.DOWN
            return format.format(value.value)
        }
        return formatTinyPrice(value.value, format)
    }

    /**
     * Renders a positive sub-unit price without rounding it away to `$0.00`. Keeps
     * [PRICE_SIGNIFICANT_DIGITS] significant figures and, once there are at least
     * [SUBSCRIPT_NOTATION_THRESHOLD] leading zeros, collapses them into subscript notation (e.g.
     * `0.00000003` -> `$0.0₇3`). Ports `formatTinyCurrencyAmount` from the shared TypeScript
     * codebase so the presentation matches desktop and the browser extension.
     */
    private fun formatTinyPrice(value: BigDecimal, format: NumberFormat): String {
        val significant =
            value
                .round(MathContext(PRICE_SIGNIFICANT_DIGITS, RoundingMode.HALF_UP))
                .stripTrailingZeros()
        // Zeros between the decimal point and the first significant digit. Computed after rounding
        // so a carry (e.g. 0.0000999 -> 0.0001) shifts the count correctly.
        val leadingZeros = significant.scale() - significant.precision()
        val significantDigits = significant.unscaledValue().abs().toString()
        val decimals =
            if (leadingZeros >= SUBSCRIPT_NOTATION_THRESHOLD) {
                "0${leadingZeros.toSubscript()}$significantDigits"
            } else {
                "0".repeat(leadingZeros) + significantDigits
            }
        val (prefix, suffix) = format.currencyAffixes()
        return "${prefix}0.$decimals$suffix"
    }

    /**
     * Formats zero with [this] to discover the characters that surround the number, so a custom
     * numeric string can be substituted while preserving the currency symbol and its
     * locale-specific placement (e.g. `$` prefix vs. ` €` suffix).
     */
    private fun NumberFormat.currencyAffixes(): Pair<String, String> {
        val iterator = formatToCharacterIterator(BigDecimal.ZERO)
        val prefix = StringBuilder()
        val suffix = StringBuilder()
        var seenNumber = false
        var char = iterator.first()
        while (char != CharacterIterator.DONE) {
            val isNumberPart = iterator.attributes.keys.any { it in NUMBER_FIELDS }
            when {
                isNumberPart -> seenNumber = true
                seenNumber -> suffix.append(char)
                else -> prefix.append(char)
            }
            char = iterator.next()
        }
        return prefix.toString() to suffix.toString()
    }

    private fun Int.toSubscript(): String =
        toString().map { SUBSCRIPT_DIGITS[it - '0'] }.joinToString("")

    private companion object {
        private const val EXTRA_FEE_PRECISION_DIGITS = 3
        private const val PRICE_SIGNIFICANT_DIGITS = 4
        private const val SUBSCRIPT_NOTATION_THRESHOLD = 4
        private val SUBSCRIPT_DIGITS = charArrayOf('₀', '₁', '₂', '₃', '₄', '₅', '₆', '₇', '₈', '₉')
        private val NUMBER_FIELDS: Set<AttributedCharacterIterator.Attribute> =
            setOf(
                NumberFormat.Field.INTEGER,
                NumberFormat.Field.FRACTION,
                NumberFormat.Field.DECIMAL_SEPARATOR,
                NumberFormat.Field.GROUPING_SEPARATOR,
                NumberFormat.Field.SIGN,
            )
    }
}

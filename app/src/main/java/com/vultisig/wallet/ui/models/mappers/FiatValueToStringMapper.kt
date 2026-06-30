package com.vultisig.wallet.ui.models.mappers

import com.vultisig.wallet.data.models.FiatValue
import com.vultisig.wallet.data.repositories.AppCurrencyRepository
import java.math.BigDecimal
import java.math.RoundingMode
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
     * When [asPrice] is true and the value is a sub-unit price, the result is rendered with enough
     * fraction digits to reveal its first significant figures, so a tiny per-token price like
     * LUNC's `$0.00006` displays as `$0.00006` instead of collapsing to `$0.00`.
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
        format.minimumFractionDigits = standardDigits
        if (asFee) {
            format.maximumFractionDigits = standardDigits + EXTRA_FEE_PRECISION_DIGITS
            format.roundingMode = RoundingMode.DOWN
        } else {
            // Number of zeros between the decimal point and the first significant digit, so we can
            // extend precision just far enough to reveal the price's leading figures.
            val leadingZeros = value.value.scale() - value.value.precision()
            format.maximumFractionDigits =
                (leadingZeros + PRICE_SIGNIFICANT_DIGITS).coerceAtMost(MAX_PRICE_FRACTION_DIGITS)
            format.roundingMode = RoundingMode.HALF_UP
        }
        return format.format(value.value)
    }

    private companion object {
        private const val EXTRA_FEE_PRECISION_DIGITS = 3
        private const val PRICE_SIGNIFICANT_DIGITS = 2
        private const val MAX_PRICE_FRACTION_DIGITS = 8
    }
}

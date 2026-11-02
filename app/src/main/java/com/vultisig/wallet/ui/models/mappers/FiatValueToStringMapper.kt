package com.vultisig.wallet.ui.models.mappers

import android.icu.number.Notation
import android.icu.number.NumberFormatter
import android.icu.number.Precision
import com.vultisig.wallet.data.models.FiatValue
import com.vultisig.wallet.data.models.settings.AppCurrency
import com.vultisig.wallet.data.repositories.AppCurrencyRepository
import com.vultisig.wallet.ui.models.token.MarketStatFormatter
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.NumberFormat
import java.util.Currency
import javax.inject.Inject
import kotlinx.coroutines.flow.first

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
     * When [compact] is true and the value is a million or more, it is rendered in the currency's
     * compact notation (`$280.78B`, `280,78 Mrd. €`), truncated so it never reads larger than the
     * value it stands for.
     *
     * All other values fall through to the currency's standard formatting. Every format uses the
     * currency's [AppCurrency.formatLocale], never the app language. [asFee] takes precedence over
     * [asPrice] when both are set.
     */
    suspend operator fun invoke(
        value: FiatValue,
        asFee: Boolean = false,
        asPrice: Boolean = false,
        compact: Boolean = false,
    ): String
}

internal class FiatValueToStringMapperImpl
@Inject
constructor(private val appCurrencyRepository: AppCurrencyRepository) : FiatValueToStringMapper {

    override suspend fun invoke(
        value: FiatValue,
        asFee: Boolean,
        asPrice: Boolean,
        compact: Boolean,
    ): String {
        val appCurrency =
            AppCurrency.fromTicker(value.currency) ?: appCurrencyRepository.currency.first()
        val currency = Currency.getInstance(value.currency)
        val format =
            (appCurrencyRepository.getCurrencyFormat(appCurrency).clone() as NumberFormat).apply {
                // Re-setting the same currency would reset an overridden symbol such as "CA$".
                if (this.currency != currency) this.currency = currency
            }
        if (compact && MarketStatFormatter.isAbbreviated(value.value)) {
            return formatCompact(value, appCurrency, format)
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

    /**
     * Compact digits from ICU, wrapped in the standard [format]'s own prefix and suffix so the
     * symbol sits, and reads, exactly as in the currency's regular amounts (`CHF 2.22 Bio.` beside
     * `CHF 1’234.50`, `CA$280.78B`) instead of following ICU's separate compact pattern.
     */
    private fun formatCompact(value: FiatValue, appCurrency: AppCurrency, format: NumberFormat) =
        (format as? DecimalFormat)?.let { decimal ->
            val digits =
                NumberFormatter.withLocale(appCurrency.formatLocale)
                    .notation(Notation.compactShort())
                    .precision(Precision.maxFraction(COMPACT_FRACTION_DIGITS))
                    .roundingMode(RoundingMode.DOWN)
                    .format(value.value)
                    .toString()
            decimal.positivePrefix + digits + decimal.positiveSuffix
        } ?: format.format(value.value)

    private companion object {
        private const val EXTRA_FEE_PRECISION_DIGITS = 3
        private const val PRICE_SIGNIFICANT_DIGITS = 2
        private const val MAX_PRICE_FRACTION_DIGITS = 8
        private const val COMPACT_FRACTION_DIGITS = 2
    }
}

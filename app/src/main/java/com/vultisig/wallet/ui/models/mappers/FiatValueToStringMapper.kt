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
     * fee like `$0.00125` displays as `$0.0012` instead of being truncated to `$0.00`. All other
     * values fall through to the currency's standard formatting.
     */
    suspend operator fun invoke(value: FiatValue, asFee: Boolean = false): String
}

internal class FiatValueToStringMapperImpl
@Inject
constructor(private val appCurrencyRepository: AppCurrencyRepository) : FiatValueToStringMapper {

    override suspend fun invoke(value: FiatValue, asFee: Boolean): String {
        val currency = Currency.getInstance(value.currency)
        val format =
            (appCurrencyRepository.getCurrencyFormat().clone() as NumberFormat).apply {
                this.currency = currency
            }
        if (!asFee) {
            return format.format(value.value)
        }
        val standardDigits = currency.defaultFractionDigits.coerceAtLeast(0)
        val subUnit = BigDecimal.ONE.movePointLeft(standardDigits)
        if (value.value <= BigDecimal.ZERO || value.value >= subUnit) {
            return format.format(value.value)
        }
        format.minimumFractionDigits = standardDigits
        format.maximumFractionDigits = standardDigits + EXTRA_FEE_PRECISION_DIGITS
        format.roundingMode = RoundingMode.DOWN
        return format.format(value.value)
    }

    private companion object {
        private const val EXTRA_FEE_PRECISION_DIGITS = 3
    }
}

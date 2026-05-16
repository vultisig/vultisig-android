package com.vultisig.wallet.ui.models.mappers

import com.vultisig.wallet.data.mappers.SuspendMapperFunc
import com.vultisig.wallet.data.models.FiatValue
import com.vultisig.wallet.data.repositories.AppCurrencyRepository
import java.math.BigDecimal
import java.text.NumberFormat
import java.util.Currency
import javax.inject.Inject

internal interface FiatValueToStringMapper : SuspendMapperFunc<FiatValue, String> {
    /**
     * Formats a fee fiat value, rendering sub-cent values (0 < value < 0.01) with 4-decimal fixed
     * precision (e.g. "$0.0015") to match iOS. Values ≥ 0.01 (and non-positive values) fall through
     * to the standard 2-decimal currency formatting used by [invoke].
     */
    suspend fun forFee(value: FiatValue): String
}

internal class FiatValueToStringMapperImpl
@Inject
constructor(private val appCurrencyRepository: AppCurrencyRepository) : FiatValueToStringMapper {

    override suspend fun invoke(from: FiatValue): String =
        from.let {
            val currencyFormat = appCurrencyRepository.getCurrencyFormat()
            currencyFormat.currency = Currency.getInstance(it.currency)
            currencyFormat.format(it.value)
        }

    override suspend fun forFee(value: FiatValue): String {
        if (value.value <= BigDecimal.ZERO || value.value >= SUB_CENT_THRESHOLD) {
            return invoke(value)
        }
        val format =
            (appCurrencyRepository.getCurrencyFormat().clone() as NumberFormat).apply {
                currency = Currency.getInstance(value.currency)
                minimumFractionDigits = SUB_CENT_FRACTION_DIGITS
                maximumFractionDigits = SUB_CENT_FRACTION_DIGITS
            }
        return format.format(value.value)
    }

    private companion object {
        private val SUB_CENT_THRESHOLD: BigDecimal = BigDecimal("0.01")
        private const val SUB_CENT_FRACTION_DIGITS = 4
    }
}

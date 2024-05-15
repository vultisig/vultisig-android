package com.vultisig.wallet.ui.models.mappers

import com.vultisig.wallet.data.mappers.MapperFunc
import com.vultisig.wallet.data.models.FiatValue
import java.text.DecimalFormat
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale
import javax.inject.Inject

/**
Maps [FiatValue] to a string representation of the value, without currency symbol.
@example FiatValue(0.1234, "USD") -> "0.12"
 */
internal interface FiatValueToValueStringMapper : MapperFunc<FiatValue, String>

internal class FiatValueToValueStringMapperImpl @Inject constructor() :
    FiatValueToValueStringMapper {

    private val currencyFormat = NumberFormat.getCurrencyInstance(Locale.getDefault())

    override fun invoke(from: FiatValue): String = from.let {
        currencyFormat.currency = Currency.getInstance(it.currency)
        val decimalFormat = (currencyFormat as DecimalFormat)
        decimalFormat.decimalFormatSymbols = decimalFormat.decimalFormatSymbols.apply {
            currencySymbol = ""
        }
        currencyFormat.format(it.value)
    }

}
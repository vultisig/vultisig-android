package com.vultisig.wallet.ui.models.mappers

import com.vultisig.wallet.data.mappers.Mapper
import com.vultisig.wallet.data.models.AppCurrency
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale
import javax.inject.Inject

internal interface ZeroValueCurrencyToStringMapper : Mapper<AppCurrency, String>

internal class ZeroValueCurrencyToStringMapperImpl @Inject constructor() : ZeroValueCurrencyToStringMapper {

    private val currencyFormat = NumberFormat.getCurrencyInstance(Locale.getDefault())

    override fun map(from: AppCurrency): String = from.let {
        currencyFormat.currency = Currency.getInstance(it.ticker)
        currencyFormat.format(0)
    }

}

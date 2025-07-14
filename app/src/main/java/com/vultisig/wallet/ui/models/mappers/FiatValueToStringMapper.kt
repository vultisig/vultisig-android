package com.vultisig.wallet.ui.models.mappers

import com.vultisig.wallet.data.mappers.Mapper
import com.vultisig.wallet.data.models.FiatValue
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale
import javax.inject.Inject

internal interface FiatValueToStringMapper : Mapper<FiatValue, String>

internal class FiatValueToStringMapperImpl @Inject constructor() : FiatValueToStringMapper {

    private val currencyFormat = NumberFormat.getCurrencyInstance(Locale.getDefault())

    override fun map(from: FiatValue): String = from.let {
        currencyFormat.currency = Currency.getInstance(it.currency)
        currencyFormat.format(it.value)
    }
}

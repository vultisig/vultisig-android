package com.vultisig.wallet.ui.models.mappers

import com.vultisig.wallet.data.mappers.SuspendMapperFunc
import com.vultisig.wallet.data.models.FiatValue
import com.vultisig.wallet.data.repositories.AppCurrencyRepository
import java.util.Currency
import javax.inject.Inject

internal interface FiatValueToStringMapper : SuspendMapperFunc<FiatValue, String>

internal class FiatValueToStringMapperImpl @Inject constructor(
    private val appCurrencyRepository: AppCurrencyRepository
) : FiatValueToStringMapper {

    override suspend fun invoke(from: FiatValue): String = from.let {
        val currencyFormat = appCurrencyRepository.getCurrencyFormat()
        currencyFormat.currency = Currency.getInstance(it.currency)
        currencyFormat.format(it.value)
    }

}

package com.vultisig.wallet.ui.models.mappers

import com.vultisig.wallet.data.mappers.SuspendMapperFunc
import com.vultisig.wallet.data.models.FiatValue
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale
import javax.inject.Inject

internal interface FiatValueToStringMapper : SuspendMapperFunc<FiatValue, String>

internal class FiatValueToStringMapperImpl @Inject constructor() : FiatValueToStringMapper {

    private val mutex = Mutex()
    private var cachedLocale: Locale? = null
    private var cachedCurrencyFormat: NumberFormat? = null

    override suspend fun invoke(from: FiatValue): String = from.let {
        val currencyFormat = getCurrencyFormat()
        currencyFormat.currency = Currency.getInstance(it.currency)
        currencyFormat.format(it.value)
    }

    private suspend fun getCurrencyFormat(): NumberFormat = mutex.withLock {
        val currentLocale = Locale.getDefault()
        // Update the currency format in a thread-safe manner when the locale changes or the value is null
        if (cachedLocale != currentLocale || cachedCurrencyFormat == null) {
            cachedLocale = currentLocale
            cachedCurrencyFormat = NumberFormat.getCurrencyInstance(currentLocale)
        }
        requireNotNull(cachedCurrencyFormat)
    }

}

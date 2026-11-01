package com.vultisig.wallet.data.repositories

import androidx.datastore.preferences.core.stringPreferencesKey
import com.vultisig.wallet.data.models.settings.AppCurrency
import com.vultisig.wallet.data.sources.AppDataStore
import java.text.DecimalFormat
import java.text.NumberFormat
import java.util.Currency
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

interface AppCurrencyRepository {

    val defaultCurrency: AppCurrency

    val currency: Flow<AppCurrency>

    suspend fun setCurrency(currency: AppCurrency)

    fun getAllCurrencies(): List<AppCurrency>

    /**
     * The format for whichever currency is selected right now, built with that currency's
     * [AppCurrency.formatLocale], never the app language.
     */
    suspend fun getCurrencyFormat(): NumberFormat

    /**
     * The format for [currency] specifically, for callers that priced their figures in a currency
     * they captured earlier. The selection can change while a load is in flight, and this stamps
     * those values with the symbol they were priced in rather than whichever one happens to be
     * selected by the time they are rendered.
     */
    suspend fun getCurrencyFormat(appCurrency: AppCurrency): NumberFormat
}

internal class AppCurrencyRepositoryImpl @Inject constructor(private val dataStore: AppDataStore) :
    AppCurrencyRepository {

    override val defaultCurrency = AppCurrency.USD

    private val mutex = Mutex()
    private val cachedFormats = mutableMapOf<AppCurrency, NumberFormat>()

    override val currency: Flow<AppCurrency>
        get() =
            dataStore.readData(stringPreferencesKey(CURRENCY_KEY), defaultCurrency.ticker).map {
                AppCurrency.fromTicker(it) ?: defaultCurrency
            }

    override suspend fun setCurrency(currency: AppCurrency) {
        dataStore.editData { preferences ->
            preferences.set(key = stringPreferencesKey(CURRENCY_KEY), value = currency.ticker)
        }
    }

    override fun getAllCurrencies(): List<AppCurrency> {
        return CURRENCY_LIST
    }

    override suspend fun getCurrencyFormat(): NumberFormat = getCurrencyFormat(currency.first())

    override suspend fun getCurrencyFormat(appCurrency: AppCurrency): NumberFormat {
        return mutex.withLock {
            val format =
                cachedFormats.getOrPut(appCurrency) {
                    NumberFormat.getCurrencyInstance(appCurrency.formatLocale).apply {
                        currency = Currency.getInstance(appCurrency.ticker)
                        val symbol = appCurrency.symbol
                        if (symbol != null && this is DecimalFormat) {
                            decimalFormatSymbols =
                                decimalFormatSymbols.apply { currencySymbol = symbol }
                        }
                    }
                }
            // Return a clone so concurrent callers never share a mutable NumberFormat instance.
            format.clone() as NumberFormat
        }
    }

    companion object {
        private const val CURRENCY_KEY = "currency_key"
    }
}

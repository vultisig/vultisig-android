package com.vultisig.wallet.data.repositories

import androidx.datastore.preferences.core.stringPreferencesKey
import com.vultisig.wallet.data.models.settings.AppCurrency
import com.vultisig.wallet.data.sources.AppDataStore
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale
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

    suspend fun getCurrencyFormat(): NumberFormat
}

internal class AppCurrencyRepositoryImpl @Inject constructor(private val dataStore: AppDataStore) :
    AppCurrencyRepository {

    override val defaultCurrency = AppCurrency.USD

    private val mutex = Mutex()
    private var cachedLocale: Locale? = null
    private var cachedCurrency: AppCurrency? = null
    private var cachedCurrencyFormat: NumberFormat? = null

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

    override suspend fun getCurrencyFormat(): NumberFormat {
        val appCurrency = currency.first()
        return mutex.withLock {
            val currentLocale = Locale.getDefault()
            // Rebuild when either the device locale or the selected app currency changes. The
            // locale
            // drives grouping/decimal conventions while the selected currency drives the symbol, so
            // a USD vault always renders "$" even when the device locale would otherwise default to
            // another currency (e.g. en_GB defaults to "£").
            if (
                cachedLocale != currentLocale ||
                    cachedCurrency != appCurrency ||
                    cachedCurrencyFormat == null
            ) {
                cachedLocale = currentLocale
                cachedCurrency = appCurrency
                cachedCurrencyFormat =
                    NumberFormat.getCurrencyInstance(currentLocale).apply {
                        currency = Currency.getInstance(appCurrency.ticker)
                    }
            }
            // Return a clone so concurrent callers never share a mutable NumberFormat instance.
            requireNotNull(cachedCurrencyFormat).clone() as NumberFormat
        }
    }

    companion object {
        private const val CURRENCY_KEY = "currency_key"
    }
}

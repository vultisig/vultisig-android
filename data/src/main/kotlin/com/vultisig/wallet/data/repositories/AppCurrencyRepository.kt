package com.vultisig.wallet.data.repositories

import androidx.datastore.preferences.core.stringPreferencesKey
import com.vultisig.wallet.data.models.settings.AppCurrency
import com.vultisig.wallet.data.sources.AppDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.text.NumberFormat
import java.util.Locale
import javax.inject.Inject

interface AppCurrencyRepository {

    val defaultCurrency: AppCurrency

    val currency: Flow<AppCurrency>


    suspend fun setCurrency(currency: AppCurrency)


    fun getAllCurrencies(): List<AppCurrency>

    suspend fun getCurrencyFormat() : NumberFormat
}

internal class AppCurrencyRepositoryImpl @Inject constructor(private val dataStore: AppDataStore) :
    AppCurrencyRepository {

    override val defaultCurrency = AppCurrency.USD

    private val mutex = Mutex()
    private var cachedLocale: Locale? = null
    private var cachedCurrencyFormat: NumberFormat? = null

    override val currency: Flow<AppCurrency>
        get() =
            dataStore.readData(stringPreferencesKey(CURRENCY_KEY), defaultCurrency.ticker)
                .map { AppCurrency.fromTicker(it) ?: defaultCurrency }

    override suspend fun setCurrency(currency: AppCurrency) {
        dataStore.editData { preferences ->
            preferences.set(key = stringPreferencesKey(CURRENCY_KEY), value = currency.ticker)
        }
    }


    override fun getAllCurrencies(): List<AppCurrency> {
        return CURRENCY_LIST
    }

    override suspend fun getCurrencyFormat(): NumberFormat = mutex.withLock {
        val currentLocale = Locale.getDefault()
        // Update the currency format in a thread-safe manner when the locale changes or the value is null
        if (cachedLocale != currentLocale || cachedCurrencyFormat == null) {
            cachedLocale = currentLocale
            cachedCurrencyFormat = NumberFormat.getCurrencyInstance(currentLocale)
        }
        requireNotNull(cachedCurrencyFormat)
    }

    companion object {
        private const val CURRENCY_KEY = "currency_key"
    }
}
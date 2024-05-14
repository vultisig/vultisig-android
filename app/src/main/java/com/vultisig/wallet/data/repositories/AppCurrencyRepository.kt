package com.vultisig.wallet.data.repositories

import androidx.datastore.preferences.core.stringPreferencesKey
import com.vultisig.wallet.data.models.AppCurrency
import com.vultisig.wallet.data.models.AppCurrency.Companion.fromTicker
import com.vultisig.wallet.data.sources.AppDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

internal interface AppCurrencyRepository {

    val defaultCurrency: AppCurrency

    val currency: Flow<AppCurrency>


    suspend fun setCurrency(currency: AppCurrency)


    fun getAllCurrencies(): List<AppCurrency>

}

internal class AppCurrencyRepositoryImpl @Inject constructor(private val dataStore: AppDataStore) : AppCurrencyRepository {

    override val defaultCurrency = AppCurrency.USD

    override val currency: Flow<AppCurrency>
        get() =
            dataStore.readData(stringPreferencesKey(CURRENCY_KEY), defaultCurrency.ticker).map { it.fromTicker() }

    override suspend fun setCurrency(currency: AppCurrency) {
        dataStore.editData { preferences ->
            preferences.set(key = stringPreferencesKey(CURRENCY_KEY), value = currency.ticker)
        }
    }


    override fun getAllCurrencies(): List<AppCurrency> {
        return CURRENCY_LIST
    }

    companion object {
        const val CURRENCY_KEY = "currency_key"
    }

}
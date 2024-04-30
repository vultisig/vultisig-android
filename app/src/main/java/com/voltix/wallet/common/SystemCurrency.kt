package com.voltix.wallet.common

import androidx.datastore.preferences.core.stringPreferencesKey
import com.voltix.wallet.data.common.data_store.AppDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

enum class SettingsCurrency(val currencyCode: String) {
    USD("USD"),
    AUD("AUD");

    companion object {
        @Inject
        lateinit var appDataStore: AppDataStore

        private val CURRENCY_KEY = stringPreferencesKey("currency")

        val currency: SettingsCurrency
            get() = runBlocking {
                val currency = appDataStore.readData(CURRENCY_KEY, "").first()
                currency.let {
                    entries.find { it.currencyCode == currency } ?: USD
                }
            }

        suspend fun setCurrency(currency: SettingsCurrency) {
            appDataStore.editData { preferences ->
                preferences[CURRENCY_KEY] = currency.currencyCode
            }
        }
    }
}
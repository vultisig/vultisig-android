package com.voltix.wallet.common

import androidx.datastore.preferences.core.stringPreferencesKey
import com.voltix.wallet.data.common.data_store.AppDataStore
import kotlinx.coroutines.flow.first

enum class SettingsCurrency(
    private val currencyCode: String
) {
    USD("USD"),
    AUD("AUD");

    companion object {

        private val CURRENCY_KEY = stringPreferencesKey("currency")
        suspend fun getCurrency(appDataStore: AppDataStore): SettingsCurrency {
            val currency = appDataStore.readData(CURRENCY_KEY, "").first()
            return entries.find { it.currencyCode == currency } ?: USD
        }

        suspend fun setCurrency(
            currency: SettingsCurrency,
            appDataStore: AppDataStore
        ) {
            appDataStore.editData { preferences ->
                preferences[CURRENCY_KEY] = currency.currencyCode
            }
        }
    }
}
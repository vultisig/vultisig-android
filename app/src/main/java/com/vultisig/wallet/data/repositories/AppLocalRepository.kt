package com.vultisig.wallet.data.repositories

import androidx.datastore.preferences.core.stringPreferencesKey
import com.vultisig.wallet.data.models.AppLanguage
import com.vultisig.wallet.data.models.AppLanguage.Companion.fromName
import com.vultisig.wallet.data.sources.AppDataStore
import com.vultisig.wallet.presenter.settings.settings_main.Language
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

internal interface AppLocaleRepository {
    val local: Flow<AppLanguage>
    suspend fun setLocale(lang: Language)
    fun getAllLocales(): List<AppLanguage>
}

internal class AppLocaleRepositoryImpl @Inject constructor(private val dataStore: AppDataStore) : AppLocaleRepository {

    private val defaultLocal = LOCAL_LIST[0]
    override val local: Flow<AppLanguage>
        get() =
            dataStore.readData(stringPreferencesKey(LOCAL_KEY), defaultLocal.mainName).map { it.fromName() }

    override suspend fun setLocale(lang: Language) {
        dataStore.editData { preferences ->
            preferences.set(key = stringPreferencesKey(LOCAL_KEY), value = lang.mainName)
        }
    }


    override fun getAllLocales(): List<AppLanguage> {
        return LOCAL_LIST
    }

    companion object {
        const val LOCAL_KEY = "local_key"
    }

}
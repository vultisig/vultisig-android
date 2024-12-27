package com.vultisig.wallet.data.repositories

import androidx.datastore.preferences.core.booleanPreferencesKey
import com.vultisig.wallet.data.sources.AppDataStore
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

interface SecretSettingsRepository {
    val isDklsEnabled: Flow<Boolean>
    suspend fun setDklsEnabled(isEnabled: Boolean)
}

internal class SecretSettingsRepositoryImpl @Inject constructor(
    private val dataStore: AppDataStore,
) : SecretSettingsRepository {

    override val isDklsEnabled: Flow<Boolean>
        get() = dataStore.readData(IS_DKLS_ENABLED_KEY, false)

    override suspend fun setDklsEnabled(isEnabled: Boolean) {
        dataStore.set(IS_DKLS_ENABLED_KEY, isEnabled)
    }

    companion object {
        val IS_DKLS_ENABLED_KEY = booleanPreferencesKey("is_dkls_enabled")
    }

}
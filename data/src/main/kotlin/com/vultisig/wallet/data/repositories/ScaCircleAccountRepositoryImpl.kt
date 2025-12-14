package com.vultisig.wallet.data.repositories

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

interface ScaCircleAccountRepository {
    suspend fun saveAccount(vaultId: String, address: String)

    suspend fun getAccount(vaultId: String): String?

    suspend fun saveCloseWarning()

    suspend fun getCloseWarning(): Boolean
}

private val Context.dataStore by preferencesDataStore(name = "circle_account_repository")

@Singleton
class ScaCircleAccountRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context
): ScaCircleAccountRepository {

    override suspend fun saveAccount(vaultId: String, address: String) {
        context.dataStore.edit { preferences ->
            preferences[selectedPositionsKey(vaultId)] = address
        }
    }

    override suspend fun getAccount(vaultId: String): String? {
        return context.dataStore.data.map { preferences ->
            preferences[selectedPositionsKey(vaultId)]
        }.firstOrNull()
    }

    override suspend fun saveCloseWarning() {
        context.dataStore.edit { preferences ->
            preferences[closeWarningScaAccount()] = true
        }
    }

    override suspend fun getCloseWarning(): Boolean {
        return context.dataStore.data.map { preferences ->
            preferences[closeWarningScaAccount()]
        }.firstOrNull() ?: false
    }

    companion object {
        private fun selectedPositionsKey(vaultId: String) =
            stringPreferencesKey("account_sca_circle_$vaultId")

        private fun closeWarningScaAccount() =
            booleanPreferencesKey("close_warning_sca_account")
    }
}
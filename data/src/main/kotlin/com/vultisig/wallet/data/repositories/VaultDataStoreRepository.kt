package com.vultisig.wallet.data.repositories

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.vultisig.wallet.data.sources.AppDataStore
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

const val GLOBAL_REMINDER_STATUS_NEVER_SHOW = -1
const val GLOBAL_REMINDER_STATUS_NOT_SET = 0

interface VaultDataStoreRepository {
    suspend fun setBackupStatus(vaultId: String, status: Boolean)

    suspend fun readBackupStatus(vaultId: String): Flow<Boolean>

    suspend fun setFastSignHint(vaultId: String, hint: String)

    suspend fun readFastSignHint(vaultId: String): Flow<String>

    suspend fun setGlobalBackupReminderStatus(month: Int)

    suspend fun readGlobalBackupReminderStatus(): Flow<Int>
}

internal class VaultDataStoreRepositoryImpl @Inject constructor(
    private val appDataStore: AppDataStore,
) : VaultDataStoreRepository {
    override suspend fun setBackupStatus(vaultId: String, status: Boolean) {
        appDataStore.editData { preferences ->
            preferences[onVaultBackupStatusKey(vaultId)] = status
        }
    }

    override suspend fun readBackupStatus(vaultId: String): Flow<Boolean> =
        appDataStore.readData(onVaultBackupStatusKey(vaultId), true)

    override suspend fun setFastSignHint(vaultId: String, hint: String) {
        appDataStore.editData { preferences ->
            preferences[onVaultFastSignHintKey(vaultId)] = hint
        }
    }

    override suspend fun readFastSignHint(vaultId: String): Flow<String> {
        return appDataStore.readData(onVaultFastSignHintKey(vaultId), "")
    }

    override suspend fun setGlobalBackupReminderStatus(month: Int) {
        appDataStore.editData { preferences ->
            preferences[onGlobalBackupReminderStatusKey()] = month
        }
    }

    override suspend fun readGlobalBackupReminderStatus(): Flow<Int> {
        return appDataStore.readData(onGlobalBackupReminderStatusKey(), GLOBAL_REMINDER_STATUS_NOT_SET)
    }

    private companion object PreferencesKey {
        fun onVaultBackupStatusKey(vaultId: String) = booleanPreferencesKey(name = "vault_backup/$vaultId")
        fun onVaultFastSignHintKey(vaultId: String) = stringPreferencesKey(name = "vault_fast_sign_hint/$vaultId")
        fun onGlobalBackupReminderStatusKey() = intPreferencesKey(name = "global_backup_reminder_status")
    }
}



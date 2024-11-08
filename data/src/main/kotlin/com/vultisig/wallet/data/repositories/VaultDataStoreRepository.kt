package com.vultisig.wallet.data.repositories

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.vultisig.wallet.data.sources.AppDataStore
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

interface VaultDataStoreRepository {
    suspend fun setBackupStatus(vaultId: String, status: Boolean)

    suspend fun readBackupStatus(vaultId: String): Flow<Boolean>

    suspend fun setBackupHint(vaultFileName: String, hint: String)

    suspend fun readBackupHint(vaultFileName: String): Flow<String>

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

    override suspend fun setBackupHint(vaultFileName: String, hint: String) {
        appDataStore.editData { preferences ->
            preferences[onVaultBackupHintKey(vaultFileName)] = hint
        }
    }

    override suspend fun readBackupHint(vaultFileName: String): Flow<String> =
        appDataStore.readData(onVaultBackupHintKey(vaultFileName), "")

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
        return appDataStore.readData(onGlobalBackupReminderStatusKey(), 0)
    }

    private companion object PreferencesKey {
        fun onVaultBackupStatusKey(vaultId: String) = booleanPreferencesKey(name = "vault_backup/$vaultId")
        fun onVaultFastSignHintKey(vaultId: String) = stringPreferencesKey(name = "vault_fast_sign_hint/$vaultId")
        fun onVaultBackupHintKey(vaultFileName: String) = stringPreferencesKey(name = "vault_backup_hint/$vaultFileName")
        fun onGlobalBackupReminderStatusKey() = intPreferencesKey(name = "global_backup_reminder_status")
    }
}



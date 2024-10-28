package com.vultisig.wallet.data.repositories

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.vultisig.wallet.data.sources.AppDataStore
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

interface VaultDataStoreRepository {
    suspend fun setBackupStatus(vaultId: String, status: Boolean)

    suspend fun readBackupStatus(vaultId: String): Flow<Boolean>

    suspend fun setBackupHint(vaultFileName: String, hint: String)

    suspend fun readBackupHint(vaultFileName: String): Flow<String>
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

    private companion object PreferencesKey {
        fun onVaultBackupStatusKey(vaultId: String) = booleanPreferencesKey(name = "vault_backup/$vaultId")
        fun onVaultBackupHintKey(vaultFileName: String) = stringPreferencesKey(name = "vault_backup_hint/$vaultFileName")
    }
}



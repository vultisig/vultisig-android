package com.vultisig.wallet.data.repositories

import androidx.datastore.preferences.core.booleanPreferencesKey
import com.vultisig.wallet.data.sources.AppDataStore
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

interface VaultDataStoreRepository {
    suspend fun setBackupStatus(vaultId: String, status: Boolean)

    suspend fun readBackupStatus(vaultId: String): Flow<Boolean>
}

internal class VaultDataStoreRepositoryImpl @Inject constructor(
    private val appDataStore: AppDataStore,
) : VaultDataStoreRepository {
    override suspend fun setBackupStatus(vaultId: String, status: Boolean) {
        appDataStore.editData { preferences ->
            preferences[onVaultBackupKey(vaultId)] = status
        }
    }

    override suspend fun readBackupStatus(vaultId: String): Flow<Boolean> =
        appDataStore.readData(onVaultBackupKey(vaultId), true)

    private companion object PreferencesKey {
        fun onVaultBackupKey(vaultId: String) = booleanPreferencesKey(name = "vault_backup/$vaultId")
    }
}



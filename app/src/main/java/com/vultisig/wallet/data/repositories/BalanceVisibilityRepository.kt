package com.vultisig.wallet.data.repositories

import androidx.datastore.preferences.core.booleanPreferencesKey
import com.vultisig.wallet.data.sources.AppDataStore
import kotlinx.coroutines.flow.first
import javax.inject.Inject

internal interface BalanceVisibilityRepository {
    suspend fun setVisibility(vaultId: String, isVisible: Boolean)
    suspend fun getVisibility(vaultId: String): Boolean
}


internal class BalanceVisibilityRepositoryImpl @Inject constructor
    (private val dataStore: AppDataStore) : BalanceVisibilityRepository {

    override suspend fun setVisibility(vaultId: String, isVisible: Boolean) {
        dataStore.set(booleanPreferencesKey(vaultId), isVisible)
    }

    override suspend fun getVisibility(vaultId: String): Boolean {
        return dataStore.readData(booleanPreferencesKey(vaultId), true).first()
    }

}
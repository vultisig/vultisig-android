package com.vultisig.wallet.data.repositories

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore(name = "tiers_nft_preferences")

interface TiersNFTRepository {
    suspend fun hasTierNFT(vaultId: String): Boolean

    suspend fun saveTierNFT(vaultId: String, hasNFT: Boolean)
}

@Singleton
class TiersNFTRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context
): TiersNFTRepository {
    override suspend fun hasTierNFT(vaultId: String): Boolean {
        return context.dataStore.data.map { preferences ->
            preferences[tierNFTKey(vaultId)] ?: false
        }.first()
    }

    override suspend fun saveTierNFT(vaultId: String, hasNFT: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[tierNFTKey(vaultId)] = hasNFT
        }
    }

    companion object {
        private fun tierNFTKey(vaultId: String) =
            booleanPreferencesKey("tier_nft_$vaultId")
    }
}
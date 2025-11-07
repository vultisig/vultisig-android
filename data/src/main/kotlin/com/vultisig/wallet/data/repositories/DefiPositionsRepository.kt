package com.vultisig.wallet.data.repositories

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.vultisig.wallet.data.models.Coins
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore(name = "defi_positions_preferences")

@Singleton
class DefiPositionsRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private fun selectedPositionsKey(vaultId: String) =
            stringSetPreferencesKey("selected_positions_$vaultId")

        private val DEFAULT_POSITIONS: Set<String> by lazy {
            setOf(
                Coins.ThorChain.RUNE,
                Coins.ThorChain.RUJI,
                Coins.ThorChain.TCY,
                Coins.ThorChain.sTCY,
                Coins.ThorChain.yRUNE,
                Coins.ThorChain.yTCY,
            ).map { it.ticker }.toSet()
        }
    }

    suspend fun saveSelectedPositions(vaultId: String, positions: List<String>) {
        context.dataStore.edit { preferences ->
            preferences[selectedPositionsKey(vaultId)] = positions.toSet()
        }
    }

    fun getSelectedPositions(vaultId: String): Flow<Set<String>> {
        return context.dataStore.data.map { preferences ->
            preferences[selectedPositionsKey(vaultId)] ?: DEFAULT_POSITIONS
        }
    }
}
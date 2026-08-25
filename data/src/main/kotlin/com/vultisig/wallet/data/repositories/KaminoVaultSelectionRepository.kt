package com.vultisig.wallet.data.repositories

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.vultisig.wallet.data.blockchain.solana.kamino.KaminoVaultRegistry
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.kaminoDataStore by preferencesDataStore(name = "kamino_vault_selection")

/**
 * Which Kamino Earn vaults a vault-holder has switched on, per Vultisig vault.
 *
 * Deliberately not folded into [DefiPositionsRepository]: that store keys selections by token
 * ticker, and Kamino's unit of choice is a vault address — two of the three curated vaults share
 * the USDC ticker, so a ticker-keyed set cannot tell them apart.
 *
 * An empty selection is a real answer, distinct from never having chosen: the first read returns
 * the default, and once the user saves anything — including nothing at all — that choice stands.
 */
@Singleton
class KaminoVaultSelectionRepository
@Inject
constructor(@ApplicationContext private val context: Context) {

    /**
     * Vault addresses the user enabled, or [DEFAULT_SELECTION] until they have chosen.
     *
     * Unknown addresses are dropped on read so a vault retired from the allow-list cannot resurrect
     * a card the app no longer knows how to price or transact against.
     */
    fun getSelectedVaults(vaultId: String): Flow<Set<String>> =
        context.kaminoDataStore.data.map { preferences ->
            val stored = preferences[hasChosenKey(vaultId)]
            val selection =
                if (stored == null) DEFAULT_SELECTION
                else preferences[selectionKey(vaultId)].orEmpty()
            selection.filterTo(mutableSetOf(), KaminoVaultRegistry::isAllowed)
        }

    /**
     * Records [vaultAddresses] as the selection, including when it is empty — which is how a user
     * turns Kamino Earn off entirely.
     */
    suspend fun saveSelectedVaults(vaultId: String, vaultAddresses: Set<String>) {
        context.kaminoDataStore.edit { preferences ->
            preferences[selectionKey(vaultId)] = vaultAddresses
            preferences[hasChosenKey(vaultId)] = CHOSEN
        }
    }

    private companion object {
        fun selectionKey(vaultId: String) = stringSetPreferencesKey("kamino_vaults_$vaultId")

        /**
         * Marks that a choice was made at all, so an empty selection reads as "turned off" rather
         * than falling back to the default and switching the vaults back on.
         */
        fun hasChosenKey(vaultId: String) = stringSetPreferencesKey("kamino_chosen_$vaultId")

        val CHOSEN = setOf("1")

        /**
         * Nothing is on until the user opts in, matching how the other DeFi position types behave.
         */
        val DEFAULT_SELECTION: Set<String> = emptySet()
    }
}

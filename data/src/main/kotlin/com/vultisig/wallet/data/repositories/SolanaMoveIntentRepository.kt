package com.vultisig.wallet.data.repositories

import androidx.datastore.preferences.core.stringPreferencesKey
import com.vultisig.wallet.data.sources.AppDataStore
import javax.inject.Inject
import kotlinx.coroutines.flow.first

/**
 * Remembers the destination validator a user picked when starting a Solana move-stake (step 1,
 * "Move SOL"). Solana can't re-delegate until the account has cooled down (~2 days), so the choice
 * must survive app restarts and pre-fill the later "Finish Move" step. Keyed by the source stake
 * account's pubkey; cleared once the move is finished.
 */
class SolanaMoveIntentRepository @Inject constructor(private val appDataStore: AppDataStore) {

    suspend fun setDestination(stakePubkey: String, votePubkey: String) {
        appDataStore.set(key(stakePubkey), votePubkey)
    }

    suspend fun getDestination(stakePubkey: String): String? =
        appDataStore.readData(key(stakePubkey)).first()

    suspend fun clear(stakePubkey: String) {
        appDataStore.editData { it.remove(key(stakePubkey)) }
    }

    private fun key(stakePubkey: String) = stringPreferencesKey("$KEY_PREFIX$stakePubkey")

    private companion object {
        const val KEY_PREFIX = "solana_move_destination_"
    }
}

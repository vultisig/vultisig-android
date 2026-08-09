package com.vultisig.wallet.data.repositories

import android.content.Context
import androidx.datastore.core.DataMigration
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.vultisig.wallet.data.models.Chain
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by
    preferencesDataStore(
        name = "defi_positions_preferences",
        produceMigrations = { listOf(SelectedPositionsPerChainMigration) },
    )

// Every DeFi screen used to share one key per vault, so each screen's save overwrote the other's
// selection. The prefix changes with the shape so the two namespaces cannot overlap and the
// migration below can recognise a pre-upgrade key by name alone.
internal const val LEGACY_SELECTED_POSITIONS_PREFIX = "selected_positions_"
private const val SELECTED_POSITIONS_PREFIX = "defi_selected_positions_"

private fun selectedPositionsKey(chain: Chain, vaultId: String) =
    stringSetPreferencesKey("$SELECTED_POSITIONS_PREFIX${chain.id}_$vaultId")

/**
 * Moves every pre-upgrade selection onto the THORChain key so nobody's current choice is reset by
 * the upgrade.
 *
 * THORChain is where the value goes because it is the screen that writes the shared key on any
 * vault that has one: MayaChain only ever persisted through it after the user opened a second
 * screen, and a Maya-authored value read back on the Maya key would be indistinguishable from a
 * genuine Maya choice. A vault whose last write happened to come from the Maya screen loses that
 * one selection here — once, at upgrade — instead of carrying the ambiguity forward forever.
 */
internal object SelectedPositionsPerChainMigration : DataMigration<Preferences> {
    override suspend fun shouldMigrate(currentData: Preferences): Boolean =
        currentData.asMap().keys.any { it.name.startsWith(LEGACY_SELECTED_POSITIONS_PREFIX) }

    override suspend fun migrate(currentData: Preferences): Preferences =
        migrateSelectedPositionsToThorchain(currentData)

    override suspend fun cleanUp() = Unit
}

internal fun migrateSelectedPositionsToThorchain(currentData: Preferences): Preferences {
    val migrated = currentData.toMutablePreferences()
    currentData.asMap().forEach { (key, value) ->
        if (!key.name.startsWith(LEGACY_SELECTED_POSITIONS_PREFIX)) return@forEach
        migrated.remove(stringSetPreferencesKey(key.name))

        val vaultId = key.name.removePrefix(LEGACY_SELECTED_POSITIONS_PREFIX)
        val positions = (value as? Set<*>)?.filterIsInstance<String>()?.toSet()
        if (vaultId.isNotEmpty() && positions != null) {
            migrated[selectedPositionsKey(Chain.ThorChain, vaultId)] = positions
        }
    }
    return migrated
}

@Singleton
class DefiPositionsRepository
@Inject
constructor(@ApplicationContext private val context: Context) {

    suspend fun saveSelectedPositions(chain: Chain, vaultId: String, positions: List<String>) {
        context.dataStore.edit { preferences ->
            preferences[selectedPositionsKey(chain, vaultId)] = positions.toSet()
        }
    }

    /**
     * Null until this vault has chosen on [chain], which is what lets a caller tell "never chose"
     * apart from a selection the user cleared on purpose — an empty set means empty and nothing
     * else. Defaults belong to the screen rather than to the store for the same reason the key is
     * per chain: they differ per chain, and one store-wide set would put one chain's positions in
     * front of another's.
     */
    fun getSelectedPositions(chain: Chain, vaultId: String): Flow<Set<String>?> =
        context.dataStore.data.map { preferences ->
            preferences[selectedPositionsKey(chain, vaultId)]
        }
}

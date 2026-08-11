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

// Spelled out rather than imported from the DeFi screens that mint these keys: the screens live in
// :app, and a migration has to keep reading the shape that was on disk when it was written even if
// those constants are later renamed.
private const val MAYA_POSITION_KEY_PREFIX = "maya:"
private const val POOL_POSITION_KEY_SEPARATOR = "."

private fun selectedPositionsKey(chain: Chain, vaultId: String) =
    stringSetPreferencesKey("$SELECTED_POSITIONS_PREFIX${chain.id}_$vaultId")

/**
 * Splits every pre-upgrade selection into the two per-chain keys so nobody's current choice is
 * reset by the upgrade.
 *
 * The shared set can hold both screens' keys at once — the THORChain dialog seeds itself from the
 * stored set and only ever toggles rows it owns, so `maya:` keys survive every later THORChain save
 * — which is why ownership is read off each key rather than off whichever screen wrote last. A
 * `maya:` key handed to THORChain would be worse than dropped: `hasBondPositions` matches it, so
 * the Bonded tab and its header leg would stay on with no row in the dialog able to switch them
 * off.
 *
 * A chain's key is only written when the set holds something that chain can own. Left out, it reads
 * back as null — never chose — and the screen answers with its own defaults, which is what that
 * vault was already being shown. Writing an empty set instead would call it a clearing and blank a
 * screen the vault never configured: a set of only `maya:` keys lit both THORChain legs before the
 * upgrade, since `hasBondPositions` and `hasStakingPositions` match the Cacao keys too.
 *
 * An empty set is written to both. That one is a clearing both screens saw, and leaving it out
 * would hand back every row the user had switched off.
 *
 * Pool keys are the one thing the shape cannot attribute — both chains write `CHAIN.TICKER` and
 * both list pools like `BTC.BTC` — so every pool is kept on both chains, a pool-only set included.
 * Landing on a chain the user did not pick it on shows a pool card they can uncheck; dropping it
 * would move a THORChain LP position onto the Maya screen and take the header total with it.
 *
 * What a pool cannot settle by itself is whether MayaChain chose at all. It counts as MayaChain
 * evidence only when the set holds nothing else, because that is exactly what the Maya dialog
 * leaves behind when both Cacao rows are unchecked. Sitting next to plain tickers the set reads as
 * THORChain's, and MayaChain's own Bond and Staking defaults serve that vault better than a lone
 * pool card.
 *
 * What that costs is worth naming: the Maya dialog seeds itself from the stored set and only
 * toggles its own rows, so a Maya selection can end up as plain tickers it never picked, and
 * nothing in the shape tells it apart from a THORChain-authored one. It gets MayaChain's defaults —
 * the same answer that screen's own read gave this shape before the upgrade.
 */
internal object SelectedPositionsPerChainMigration : DataMigration<Preferences> {
    override suspend fun shouldMigrate(currentData: Preferences): Boolean =
        currentData.asMap().keys.any { it.name.startsWith(LEGACY_SELECTED_POSITIONS_PREFIX) }

    override suspend fun migrate(currentData: Preferences): Preferences =
        migrateSelectedPositionsPerChain(currentData)

    override suspend fun cleanUp() = Unit
}

internal fun migrateSelectedPositionsPerChain(currentData: Preferences): Preferences {
    val migrated = currentData.toMutablePreferences()
    currentData.asMap().forEach { (key, value) ->
        if (!key.name.startsWith(LEGACY_SELECTED_POSITIONS_PREFIX)) return@forEach
        migrated.remove(stringSetPreferencesKey(key.name))

        val vaultId = key.name.removePrefix(LEGACY_SELECTED_POSITIONS_PREFIX)
        val positions = (value as? Set<*>)?.filterIsInstance<String>()?.toSet()
        if (vaultId.isEmpty() || positions == null) return@forEach

        val mayaPositions = positions.filterTo(mutableSetOf()) { it.isMayaPositionKey() }
        val thorchainPositions = positions - mayaPositions
        val poolPositions = thorchainPositions.filterTo(mutableSetOf()) { it.isPoolPositionKey() }

        // A set of only `maya:` keys says nothing about THORChain, and recording it as a clearing
        // would blank a screen that was showing both its legs before the upgrade.
        if (positions.isEmpty() || thorchainPositions.isNotEmpty()) {
            migrated[selectedPositionsKey(Chain.ThorChain, vaultId)] = thorchainPositions
        }

        // A `maya:` key is the only attribution the shape carries. Pools are the exception the
        // Maya dialog forces: unchecking both Cacao rows leaves a set with no `maya:` key at all,
        // so a set of nothing but pools is a MayaChain choice too — a choice it shares with
        // THORChain above, since the pool itself stays unattributable. Pools sitting next to plain
        // tickers do not count — that set reads as THORChain's, and Maya's defaults beat a lone
        // pool.
        val isPoolOnlySelection = positions.isNotEmpty() && positions == poolPositions
        if (positions.isEmpty() || mayaPositions.isNotEmpty() || isPoolOnlySelection) {
            migrated[selectedPositionsKey(Chain.MayaChain, vaultId)] = mayaPositions + poolPositions
        }
    }
    return migrated
}

private fun String.isMayaPositionKey() = startsWith(MAYA_POSITION_KEY_PREFIX)

private fun String.isPoolPositionKey() = contains(POOL_POSITION_KEY_SEPARATOR)

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

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

// What each screen answers a vault that never chose with, frozen at the shape they had when this
// migration shipped for the same reason the key prefixes are. Only ever written next to a pool: it
// is how an unattributable pool is kept without the vault being recorded as having chosen anything
// else, and a vault that reaches this migration with no pool at all is left without a key instead.
private val MAYA_DEFAULT_POSITION_KEYS = setOf("maya:bond:CACAO", "maya:stake:CACAO")
private val THORCHAIN_DEFAULT_POSITION_KEYS = setOf("RUNE", "RUJI", "TCY", "sTCY", "yRUNE", "yTCY")

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
 * Only the `maya:` keys and the plain tickers attribute themselves: the first pair can only have
 * come from the Maya dialog, the second only from THORChain's. A chain with none of its own keys in
 * the set has not been heard from, and its key is left unwritten, which reads back as null — never
 * chose — so the screen answers with its own defaults. Writing an empty set instead would call it a
 * clearing and blank a screen the vault never configured: a set of only `maya:` keys lit both
 * THORChain legs before the upgrade, since `hasBondPositions` and `hasStakingPositions` match the
 * Cacao keys too.
 *
 * An empty set is written to both. That one is a clearing both screens saw, and leaving it out
 * would hand back every row the user had switched off.
 *
 * Pool keys attribute nothing — both chains write `CHAIN.TICKER` and both list pools like `BTC.BTC`
 * — so a pool is never evidence that its chain chose, and never dropped either. It rides along to
 * both, next to whatever each chain has of its own, or next to that chain's defaults where it has
 * nothing. Landing on a chain the user did not pick it on shows a pool card they can uncheck;
 * dropping it would empty an LP tab that was showing it before the upgrade.
 *
 * A set of nothing but pools is the exception on both sides: it is the whole selection each screen
 * had, and it is what the Maya dialog leaves behind when both Cacao rows are unchecked, so pairing
 * it with the defaults would hand back the rows that had to come off to leave that shape.
 *
 * What that costs is worth naming: each dialog seeds itself from the stored set and only toggles
 * its own rows, so a Maya selection can end up as plain tickers it never picked, and nothing in the
 * shape tells it apart from a THORChain-authored one. It gets MayaChain's defaults — the same
 * answer that screen's own read gave this shape before the upgrade.
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
        val tickerPositions = thorchainPositions - poolPositions

        selectionFor(positions, tickerPositions, poolPositions, THORCHAIN_DEFAULT_POSITION_KEYS)
            ?.let { migrated[selectedPositionsKey(Chain.ThorChain, vaultId)] = it }

        selectionFor(positions, mayaPositions, poolPositions, MAYA_DEFAULT_POSITION_KEYS)?.let {
            migrated[selectedPositionsKey(Chain.MayaChain, vaultId)] = it
        }
    }
    return migrated
}

/**
 * What one chain takes from a pre-upgrade set, or null to leave its key unwritten so the screen
 * answers with its own defaults. [ownKeys] is the part of the set only this chain can have written:
 * the `maya:` keys for MayaChain, the plain tickers for THORChain. Pools belong to neither and are
 * never evidence for either — they are only ever carried.
 */
private fun selectionFor(
    positions: Set<String>,
    ownKeys: Set<String>,
    poolPositions: Set<String>,
    defaults: Set<String>,
): Set<String>? =
    when {
        // A clearing both screens saw.
        positions.isEmpty() -> emptySet()
        // Nothing but pools is the whole selection on both screens, and pairing it with the
        // defaults would hand back the rows that had to be unchecked to leave this shape behind.
        positions == poolPositions -> poolPositions
        ownKeys.isNotEmpty() -> ownKeys + poolPositions
        // No say of its own, but a pool that may still be this chain's. Dropping it would empty an
        // LP tab that was showing it; keeping it alone would read as a choice this vault never
        // made.
        poolPositions.isNotEmpty() -> defaults + poolPositions
        else -> null
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

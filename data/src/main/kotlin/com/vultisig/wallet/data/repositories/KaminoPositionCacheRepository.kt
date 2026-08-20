package com.vultisig.wallet.data.repositories

import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.vultisig.wallet.data.blockchain.solana.kamino.KaminoVaultRegistry
import com.vultisig.wallet.data.sources.AppDataStore
import java.math.BigInteger
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

/**
 * The last known size of each Kamino Earn position, in the vault's underlying token base units.
 *
 * The DeFi portfolio reads balances from a cache first and only then from the network, so without a
 * snapshot a position would render as nothing on every cold start until Kamino answered. Kamino has
 * no cheap "just the balance" endpoint — a position is shares times a live share price — so the
 * derived amount is what gets kept rather than the inputs. iOS persists the same snapshot for the
 * same reason.
 *
 * Keyed by kVault address, not by token: two of the three curated vaults are USDC, and a
 * token-keyed store could not tell their positions apart.
 */
@Singleton
class KaminoPositionCacheRepository @Inject constructor(private val dataStore: AppDataStore) {

    /**
     * Amounts by kVault address. Vaults the allow-list no longer carries are dropped on read, so a
     * retired vault cannot keep contributing to a total the app can no longer price.
     */
    suspend fun getPositions(vaultId: String): Map<String, BigInteger> =
        decodeAll(dataStore.readData(positionsKey(vaultId)).first().orEmpty())

    /**
     * Merges [positions] into the snapshot: a vault reported as holding nothing stops counting — it
     * arrives as zero — while a vault this write says nothing about keeps what it had.
     *
     * Deliberately not a wholesale replace. The caller only ever reports the vaults the user has
     * switched on, so replacing would wipe a switched-off vault's amount, and switching it back on
     * would leave the next cold start short by that position until the network answered again.
     */
    suspend fun savePositions(vaultId: String, positions: Map<String, BigInteger>) {
        dataStore.editData { preferences ->
            val key = positionsKey(vaultId)
            val merged = decodeAll(preferences[key].orEmpty()) + positions
            preferences[key] =
                merged.map { (address, amount) -> "$address$SEPARATOR$amount" }.toSet()
        }
    }

    private companion object {
        const val SEPARATOR = "="

        fun positionsKey(vaultId: String) = stringSetPreferencesKey("kamino_positions_$vaultId")

        fun decodeAll(stored: Set<String>): Map<String, BigInteger> =
            stored.mapNotNull(::decode).filter { KaminoVaultRegistry.isAllowed(it.first) }.toMap()

        /** A malformed entry is dropped rather than read as zero, which would report a loss. */
        fun decode(entry: String): Pair<String, BigInteger>? {
            val address = entry.substringBefore(SEPARATOR, missingDelimiterValue = "")
            val amount = entry.substringAfter(SEPARATOR, missingDelimiterValue = "")
            if (address.isEmpty()) return null
            return amount.toBigIntegerOrNull()?.let { address to it }
        }
    }
}

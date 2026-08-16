package com.vultisig.wallet.data.repositories

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.vultisig.wallet.data.blockchain.solana.kamino.KaminoVaultRegistry
import dagger.hilt.android.qualifiers.ApplicationContext
import java.math.BigInteger
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

private val Context.kaminoPositionDataStore by preferencesDataStore(name = "kamino_positions")

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
class KaminoPositionCacheRepository
@Inject
constructor(@ApplicationContext private val context: Context) {

    /**
     * Amounts by kVault address. Vaults the allow-list no longer carries are dropped on read, so a
     * retired vault cannot keep contributing to a total the app can no longer price.
     */
    suspend fun getPositions(vaultId: String): Map<String, BigInteger> {
        val stored = context.kaminoPositionDataStore.data.first()[positionsKey(vaultId)].orEmpty()
        return stored
            .mapNotNull(::decode)
            .filter { KaminoVaultRegistry.isAllowed(it.first) }
            .toMap()
    }

    /** Replaces the snapshot wholesale, so a vault that no longer holds anything stops counting. */
    suspend fun savePositions(vaultId: String, positions: Map<String, BigInteger>) {
        context.kaminoPositionDataStore.edit { preferences ->
            preferences[positionsKey(vaultId)] =
                positions.map { (address, amount) -> "$address$SEPARATOR$amount" }.toSet()
        }
    }

    private companion object {
        const val SEPARATOR = "="

        fun positionsKey(vaultId: String) = stringSetPreferencesKey("kamino_positions_$vaultId")

        /** A malformed entry is dropped rather than read as zero, which would report a loss. */
        fun decode(entry: String): Pair<String, BigInteger>? {
            val address = entry.substringBefore(SEPARATOR, missingDelimiterValue = "")
            val amount = entry.substringAfter(SEPARATOR, missingDelimiterValue = "")
            if (address.isEmpty()) return null
            return amount.toBigIntegerOrNull()?.let { address to it }
        }
    }
}

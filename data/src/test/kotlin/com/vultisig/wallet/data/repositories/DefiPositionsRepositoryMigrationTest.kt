package com.vultisig.wallet.data.repositories

import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class DefiPositionsRepositoryMigrationTest {

    @Test
    fun `a pre-upgrade selection moves to the Thorchain key`() = runTest {
        val before = preferencesWithLegacySelection(VAULT_ID, setOf("RUNE", "TCY"))

        val after = migrateSelectedPositionsToThorchain(before)

        assertEquals(setOf("RUNE", "TCY"), after[thorchainKey(VAULT_ID)])
        assertNull(after[legacyKey(VAULT_ID)])
    }

    @Test
    fun `every vault is moved, not just the first`() = runTest {
        val before =
            mutablePreferencesOf(
                legacyKey("vault-a") to setOf("RUNE"),
                legacyKey("vault-b") to setOf("TCY", "BTC.BTC"),
            )

        val after = migrateSelectedPositionsToThorchain(before)

        assertEquals(setOf("RUNE"), after[thorchainKey("vault-a")])
        assertEquals(setOf("TCY", "BTC.BTC"), after[thorchainKey("vault-b")])
        assertNull(after[legacyKey("vault-a")])
        assertNull(after[legacyKey("vault-b")])
    }

    @Test
    fun `a cleared selection stays cleared instead of reverting to the defaults`() = runTest {
        // Dropping an empty set here would read back as "never chose" and put every position the
        // user had unchecked in front of them again on the first launch after the upgrade.
        val before = preferencesWithLegacySelection(VAULT_ID, emptySet())

        val after = migrateSelectedPositionsToThorchain(before)

        assertEquals(emptySet<String>(), after[thorchainKey(VAULT_ID)])
    }

    @Test
    fun `preferences outside the selection namespace are left alone`() = runTest {
        val unrelated = stringPreferencesKey("some_other_preference")
        val before =
            mutablePreferencesOf(legacyKey(VAULT_ID) to setOf("RUNE"), unrelated to "untouched")

        val after = migrateSelectedPositionsToThorchain(before)

        assertEquals("untouched", after[unrelated])
    }

    @Test
    fun `the migration stops asking to run once it has run`() = runTest {
        val before = preferencesWithLegacySelection(VAULT_ID, setOf("RUNE"))
        assertTrue(SelectedPositionsPerChainMigration.shouldMigrate(before))

        val after = SelectedPositionsPerChainMigration.migrate(before)

        // shouldMigrate is consulted on every store creation, so a key it still recognises would
        // re-run the move forever — and the second run would overwrite a Thorchain selection the
        // user had made since with the pre-upgrade one.
        assertFalse(SelectedPositionsPerChainMigration.shouldMigrate(after))
    }

    @Test
    fun `a per-chain key written after the upgrade is not mistaken for a legacy one`() = runTest {
        val before =
            mutablePreferencesOf(
                thorchainKey(VAULT_ID) to setOf("RUNE"),
                mayachainKey(VAULT_ID) to setOf("maya:bond:CACAO"),
            )

        assertFalse(SelectedPositionsPerChainMigration.shouldMigrate(before))

        val after = migrateSelectedPositionsToThorchain(before)

        assertEquals(setOf("RUNE"), after[thorchainKey(VAULT_ID)])
        assertEquals(setOf("maya:bond:CACAO"), after[mayachainKey(VAULT_ID)])
    }

    private fun preferencesWithLegacySelection(vaultId: String, positions: Set<String>) =
        mutablePreferencesOf(legacyKey(vaultId) to positions)

    private fun legacyKey(vaultId: String) =
        stringSetPreferencesKey("$LEGACY_SELECTED_POSITIONS_PREFIX$vaultId")

    private fun thorchainKey(vaultId: String): Preferences.Key<Set<String>> =
        stringSetPreferencesKey("defi_selected_positions_THORChain_$vaultId")

    private fun mayachainKey(vaultId: String): Preferences.Key<Set<String>> =
        stringSetPreferencesKey("defi_selected_positions_MayaChain_$vaultId")

    private companion object {
        const val VAULT_ID = "vault-id"
    }
}

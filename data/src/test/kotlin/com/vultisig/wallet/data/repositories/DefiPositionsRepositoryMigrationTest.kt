package com.vultisig.wallet.data.repositories

import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

internal class DefiPositionsRepositoryMigrationTest {

    @Test
    fun `a pre-upgrade Thorchain selection moves to the Thorchain key`() = runTest {
        val before = preferencesWithLegacySelection(VAULT_ID, setOf("RUNE", "TCY"))

        val after = migrateSelectedPositionsPerChain(before)

        after[thorchainKey(VAULT_ID)] shouldBe setOf("RUNE", "TCY")
        after[legacyKey(VAULT_ID)].shouldBeNull()
    }

    @Test
    fun `every vault is moved, not just the first`() = runTest {
        val before =
            mutablePreferencesOf(
                legacyKey("vault-a") to setOf("RUNE"),
                legacyKey("vault-b") to setOf("TCY", "BTC.BTC"),
            )

        val after = migrateSelectedPositionsPerChain(before)

        after[thorchainKey("vault-a")] shouldBe setOf("RUNE")
        after[thorchainKey("vault-b")] shouldBe setOf("TCY", "BTC.BTC")
        after[legacyKey("vault-a")].shouldBeNull()
        after[legacyKey("vault-b")].shouldBeNull()
    }

    @Test
    fun `a Maya key never lands on Thorchain`() = runTest {
        // hasBondPositions matches maya:bond:CACAO, so leaving it here would light up the Bonded
        // tab and its header leg on a screen whose dialog has no row that can switch it back off.
        val before =
            preferencesWithLegacySelection(
                VAULT_ID,
                setOf("RUNE", MAYA_BOND_CACAO_KEY, MAYA_STAKE_CACAO_KEY),
            )

        val after = migrateSelectedPositionsPerChain(before)

        after[thorchainKey(VAULT_ID)] shouldBe setOf("RUNE")
    }

    @Test
    fun `a Maya selection is kept instead of being reset to the Maya defaults`() = runTest {
        val before =
            preferencesWithLegacySelection(VAULT_ID, setOf("RUNE", "TCY", MAYA_STAKE_CACAO_KEY))

        val after = migrateSelectedPositionsPerChain(before)

        after[mayachainKey(VAULT_ID)] shouldBe setOf(MAYA_STAKE_CACAO_KEY)
        after[thorchainKey(VAULT_ID)] shouldBe setOf("RUNE", "TCY")
    }

    @Test
    fun `a pool the shape cannot attribute is kept on both chains`() = runTest {
        // Both screens write CHAIN.TICKER and both list BTC.BTC, so there is nothing in the key to
        // say who chose it. A pool shown on the chain it was not chosen on has a dialog row and can
        // be unchecked; a dropped one just leaves the header total short.
        val before =
            preferencesWithLegacySelection(VAULT_ID, setOf(MAYA_STAKE_CACAO_KEY, "BTC.BTC"))

        val after = migrateSelectedPositionsPerChain(before)

        after[mayachainKey(VAULT_ID)] shouldBe setOf(MAYA_STAKE_CACAO_KEY, "BTC.BTC")
        after[thorchainKey(VAULT_ID)] shouldBe setOf("BTC.BTC")
    }

    @Test
    fun `a vault that never chose on Maya is left without a Maya key`() = runTest {
        // Plain tickers are the only shape the Maya screen cannot have written, and the one it
        // already answered with its defaults before the upgrade. Writing a key for it would leave
        // a screen the user has never opened with nothing selected.
        val before = preferencesWithLegacySelection(VAULT_ID, setOf("RUNE", "TCY"))

        val after = migrateSelectedPositionsPerChain(before)

        after[mayachainKey(VAULT_ID)].shouldBeNull()
    }

    @Test
    fun `unchecking both Cacao rows is a Maya choice, not an absent one`() = runTest {
        // A Maya selection down to a pool holds no maya: key at all. Reading that as "never chose"
        // would revive Bond and Staking and drop the pool the user had kept.
        val before = preferencesWithLegacySelection(VAULT_ID, setOf("BTC.BTC"))

        val after = migrateSelectedPositionsPerChain(before)

        after[mayachainKey(VAULT_ID)] shouldBe setOf("BTC.BTC")
    }

    @Test
    fun `a cleared selection stays cleared instead of reverting to the defaults`() = runTest {
        // Dropping an empty set here would read back as "never chose" and put every position the
        // user had unchecked in front of them again on the first launch after the upgrade.
        val before = preferencesWithLegacySelection(VAULT_ID, emptySet())

        val after = migrateSelectedPositionsPerChain(before)

        after[thorchainKey(VAULT_ID)] shouldBe emptySet()
        after[mayachainKey(VAULT_ID)] shouldBe emptySet()
    }

    @Test
    fun `preferences outside the selection namespace are left alone`() = runTest {
        val unrelated = stringPreferencesKey("some_other_preference")
        val before =
            mutablePreferencesOf(legacyKey(VAULT_ID) to setOf("RUNE"), unrelated to "untouched")

        val after = migrateSelectedPositionsPerChain(before)

        after[unrelated] shouldBe "untouched"
    }

    @Test
    fun `the migration stops asking to run once it has run`() = runTest {
        val before = preferencesWithLegacySelection(VAULT_ID, setOf("RUNE"))
        SelectedPositionsPerChainMigration.shouldMigrate(before) shouldBe true

        val after = SelectedPositionsPerChainMigration.migrate(before)

        // shouldMigrate is consulted on every store creation, so a key it still recognises would
        // re-run the move forever — and the second run would overwrite a Thorchain selection the
        // user had made since with the pre-upgrade one.
        SelectedPositionsPerChainMigration.shouldMigrate(after) shouldBe false
    }

    @Test
    fun `a per-chain key written after the upgrade is not mistaken for a legacy one`() = runTest {
        val before =
            mutablePreferencesOf(
                thorchainKey(VAULT_ID) to setOf("RUNE"),
                mayachainKey(VAULT_ID) to setOf(MAYA_BOND_CACAO_KEY),
            )

        SelectedPositionsPerChainMigration.shouldMigrate(before) shouldBe false

        val after = migrateSelectedPositionsPerChain(before)

        after[thorchainKey(VAULT_ID)] shouldBe setOf("RUNE")
        after[mayachainKey(VAULT_ID)] shouldBe setOf(MAYA_BOND_CACAO_KEY)
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
        // The DeFi screens that mint these live in :app; spelled out here for the same reason the
        // migration spells out the prefix it matches on.
        const val MAYA_BOND_CACAO_KEY = "maya:bond:CACAO"
        const val MAYA_STAKE_CACAO_KEY = "maya:stake:CACAO"
    }
}

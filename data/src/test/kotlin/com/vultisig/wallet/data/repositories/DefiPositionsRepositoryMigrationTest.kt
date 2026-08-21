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
        // Thorchain has no key of its own in this set, so the pool arrives next to its defaults
        // rather than alone — alone it would read as a selection this vault never made and hide a
        // bonded RUNE the pre-upgrade screen was showing.
        after[thorchainKey(VAULT_ID)] shouldBe THORCHAIN_DEFAULTS + "BTC.BTC"
    }

    @Test
    fun `a Maya picked pool does not silence the Thorchain screen`() = runTest {
        // A Maya-first vault that ticked one LP pool never touched the Thorchain dialog. Reading
        // its pool as Thorchain evidence would write a key holding only that pool, and
        // hasBondPositions would stop matching the Cacao key that kept the bonded leg alive.
        val before =
            preferencesWithLegacySelection(
                VAULT_ID,
                setOf(MAYA_BOND_CACAO_KEY, MAYA_STAKE_CACAO_KEY, "BTC.BTC"),
            )

        val after = migrateSelectedPositionsPerChain(before)

        after[thorchainKey(VAULT_ID)] shouldBe THORCHAIN_DEFAULTS + "BTC.BTC"
        after[mayachainKey(VAULT_ID)] shouldBe
            setOf(MAYA_BOND_CACAO_KEY, MAYA_STAKE_CACAO_KEY, "BTC.BTC")
    }

    @Test
    fun `a vault that never chose on Maya is left without a Maya key`() = runTest {
        // Plain tickers carry no attribution to Maya, and its own read already answered that shape
        // with its defaults before the upgrade. Writing a key for it would leave a screen the user
        // has never opened with nothing selected.
        val before = preferencesWithLegacySelection(VAULT_ID, setOf("RUNE", "TCY"))

        val after = migrateSelectedPositionsPerChain(before)

        after[mayachainKey(VAULT_ID)].shouldBeNull()
    }

    @Test
    fun `a pool next to tickers is kept on Maya without being read as its choice`() = runTest {
        // Thorchain pool keys are dotted too, so a pool cannot stand as proof of a Maya choice.
        // Handing Maya just that pool would drop the Bond leg off a screen this vault may never
        // have chosen on; dropping it would empty an LP tab that had it, if the pool was Maya's.
        val before = preferencesWithLegacySelection(VAULT_ID, setOf("RUNE", "BTC.BTC"))

        val after = migrateSelectedPositionsPerChain(before)

        after[mayachainKey(VAULT_ID)] shouldBe MAYA_DEFAULTS + "BTC.BTC"
        after[thorchainKey(VAULT_ID)] shouldBe setOf("RUNE", "BTC.BTC")
    }

    @Test
    fun `unchecking both Cacao rows is a Maya choice, not an absent one`() = runTest {
        // A Maya selection down to a pool holds no maya: key at all. Reading that as "never chose"
        // would revive Bond and Staking and drop the pool the user had kept.
        val before = preferencesWithLegacySelection(VAULT_ID, setOf("BTC.BTC"))

        val after = migrateSelectedPositionsPerChain(before)

        after[mayachainKey(VAULT_ID)] shouldBe setOf("BTC.BTC")
        // Counting for Maya does not make it Maya's alone: the key is as unattributable here as it
        // is in any other set. Withholding it from Thorchain would move the LP position of a vault
        // that cleared its tickers onto the other chain's screen, and hand it back the defaults it
        // had just unchecked.
        after[thorchainKey(VAULT_ID)] shouldBe setOf("BTC.BTC")
    }

    @Test
    fun `a vault that never chose on Thorchain is left without a Thorchain key`() = runTest {
        // hasBondPositions and hasStakingPositions both match the Cacao keys, so this vault's
        // Thorchain screen had both legs on before the upgrade. Recording an empty set would call
        // that a clearing and blank it; absent leaves the screen its own defaults.
        val before =
            preferencesWithLegacySelection(
                VAULT_ID,
                setOf(MAYA_BOND_CACAO_KEY, MAYA_STAKE_CACAO_KEY),
            )

        val after = migrateSelectedPositionsPerChain(before)

        after[thorchainKey(VAULT_ID)].shouldBeNull()
        after[mayachainKey(VAULT_ID)] shouldBe setOf(MAYA_BOND_CACAO_KEY, MAYA_STAKE_CACAO_KEY)
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

    // ──────── positions introduced after a vault had already chosen ────────

    @Test
    fun `a selection saved before ybRUNE existed gets the new position switched on`() = runTest {
        // The stored set is handed back verbatim, so without this the compounded bRUNE card and
        // its leg of the tab total stay hidden on every vault that had ever opened the dialog —
        // with no row switched off to explain why.
        val before = mutablePreferencesOf(thorchainKey(VAULT_ID) to setOf("RUNE", "TCY"))

        val after = addNewThorchainPositions(before)

        after[thorchainKey(VAULT_ID)] shouldBe setOf("RUNE", "TCY", "ybRUNE")
    }

    @Test
    fun `every vault gets the new position, not just the first`() = runTest {
        val before =
            mutablePreferencesOf(
                thorchainKey("vault-a") to setOf("RUNE"),
                thorchainKey("vault-b") to setOf("TCY", "BTC.BTC"),
            )

        val after = addNewThorchainPositions(before)

        after[thorchainKey("vault-a")] shouldBe setOf("RUNE", "ybRUNE")
        after[thorchainKey("vault-b")] shouldBe setOf("TCY", "BTC.BTC", "ybRUNE")
    }

    @Test
    fun `a cleared selection is not handed a row it never asked for`() = runTest {
        val before = mutablePreferencesOf(thorchainKey(VAULT_ID) to emptySet<String>())

        val after = addNewThorchainPositions(before)

        after[thorchainKey(VAULT_ID)] shouldBe emptySet()
    }

    @Test
    fun `a vault that never chose is left without a key`() = runTest {
        // Null is what tells the screen to answer with its own defaults, which already carry the
        // new position. Writing a set here would turn that into a choice the vault never made.
        val after = addNewThorchainPositions(mutablePreferencesOf())

        after[thorchainKey(VAULT_ID)].shouldBeNull()
    }

    @Test
    fun `the Maya selection is left alone`() = runTest {
        val before = mutablePreferencesOf(mayachainKey(VAULT_ID) to setOf(MAYA_BOND_CACAO_KEY))

        val after = addNewThorchainPositions(before)

        after[mayachainKey(VAULT_ID)] shouldBe setOf(MAYA_BOND_CACAO_KEY)
    }

    @Test
    fun `switching the new position back off survives the next launch`() = runTest {
        // shouldMigrate is consulted on every store creation. Without the version counter this
        // would be a standing rule that undid the user's choice on the very next cold start.
        val before = mutablePreferencesOf(thorchainKey(VAULT_ID) to setOf("RUNE"))
        NewThorchainPositionsMigration.shouldMigrate(before) shouldBe true

        val migrated = NewThorchainPositionsMigration.migrate(before)
        val userSwitchedItOff =
            migrated.toMutablePreferences().apply { this[thorchainKey(VAULT_ID)] = setOf("RUNE") }

        NewThorchainPositionsMigration.shouldMigrate(userSwitchedItOff) shouldBe false
        addNewThorchainPositions(userSwitchedItOff)[thorchainKey(VAULT_ID)] shouldBe setOf("RUNE")
    }

    @Test
    fun `a fresh install is only stamped with the current version`() = runTest {
        val after = NewThorchainPositionsMigration.migrate(mutablePreferencesOf())

        NewThorchainPositionsMigration.shouldMigrate(after) shouldBe false
        after.asMap().keys.map { it.name } shouldBe listOf("defi_selected_positions_version")
    }

    @Test
    fun `the legacy split runs first, so the vault it moves gets the new position too`() = runTest {
        // Both migrations run in one pass, in list order, each seeing the previous one's output.
        val before = preferencesWithLegacySelection(VAULT_ID, setOf("RUNE", "TCY"))

        val after = addNewThorchainPositions(migrateSelectedPositionsPerChain(before))

        after[thorchainKey(VAULT_ID)] shouldBe setOf("RUNE", "TCY", "ybRUNE")
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
        // What each screen shows a vault that never chose, spelled out here for the same reason.
        val MAYA_DEFAULTS = setOf(MAYA_BOND_CACAO_KEY, MAYA_STAKE_CACAO_KEY)
        val THORCHAIN_DEFAULTS = setOf("RUNE", "RUJI", "TCY", "sTCY", "yRUNE", "yTCY")
    }
}

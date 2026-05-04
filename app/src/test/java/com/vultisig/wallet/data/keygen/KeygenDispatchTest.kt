package com.vultisig.wallet.data.keygen

import com.vultisig.wallet.data.models.SigningLibType
import com.vultisig.wallet.data.models.TssAction
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test

/**
 * Pins down the (action, libType) → executor dispatch matrix.
 *
 * The original batch reshare PR enabled `isBatchEligibleReshare(KeyImport) = true` at the predicate
 * layer, but the inline dispatch in `KeygenViewModel.generateKey()` still routed KeyImport vaults
 * to `startKeyImportKeygen`, which errors for `TssAction.ReShare`. The predicate-level tests
 * passed; the dispatch bug shipped to a draft PR. CodeRabbit caught it.
 *
 * These tests exercise [selectKeygenExecutor] directly so the executor decision is verifiable in
 * isolation from JNI-heavy ceremony code, closing the gap class.
 */
class KeygenDispatchTest {

    // -- ReShare action --

    @Test
    fun `ReShare on a DKLS vault dispatches to the DKLS executor`() {
        assertEquals(
            KeygenExecutor.DklsKeygen,
            selectKeygenExecutor(TssAction.ReShare, SigningLibType.DKLS),
        )
    }

    @Test
    fun `ReShare on a KeyImport vault dispatches to the DKLS executor — would have caught the original bug`() {
        // Pre-fix this routed to KeyImportKeygen, which errors with
        // "No key import data found" because that executor only handles fresh seed-phrase
        // imports.
        assertEquals(
            KeygenExecutor.DklsKeygen,
            selectKeygenExecutor(TssAction.ReShare, SigningLibType.KeyImport),
        )
    }

    @Test
    fun `ReShare on a GG20 vault dispatches to the GG20 executor`() {
        assertEquals(
            KeygenExecutor.Gg20Keygen,
            selectKeygenExecutor(TssAction.ReShare, SigningLibType.GG20),
        )
    }

    // -- KEYGEN action --

    @Test
    fun `KEYGEN on DKLS dispatches to the DKLS executor`() {
        assertEquals(
            KeygenExecutor.DklsKeygen,
            selectKeygenExecutor(TssAction.KEYGEN, SigningLibType.DKLS),
        )
    }

    @Test
    fun `KEYGEN on GG20 dispatches to the GG20 executor`() {
        assertEquals(
            KeygenExecutor.Gg20Keygen,
            selectKeygenExecutor(TssAction.KEYGEN, SigningLibType.GG20),
        )
    }

    // -- Migrate action --

    @Test
    fun `Migrate on DKLS dispatches to the DKLS executor`() {
        // Navigation forces libType=DKLS for Migrate, so this is the realistic combination.
        assertEquals(
            KeygenExecutor.DklsKeygen,
            selectKeygenExecutor(TssAction.Migrate, SigningLibType.DKLS),
        )
    }

    @Test
    fun `Migrate on GG20 dispatches to the GG20 executor`() {
        assertEquals(
            KeygenExecutor.Gg20Keygen,
            selectKeygenExecutor(TssAction.Migrate, SigningLibType.GG20),
        )
    }

    // -- KeyImport action --

    @Test
    fun `KeyImport action on a KeyImport libType dispatches to the KeyImport executor`() {
        assertEquals(
            KeygenExecutor.KeyImportKeygen,
            selectKeygenExecutor(TssAction.KeyImport, SigningLibType.KeyImport),
        )
    }

    // -- SingleKeygen action --

    @Test
    fun `SingleKeygen dispatches to the single-keygen executor regardless of libType`() {
        assertEquals(
            KeygenExecutor.SingleKeygen,
            selectKeygenExecutor(TssAction.SingleKeygen, SigningLibType.DKLS),
        )
        assertEquals(
            KeygenExecutor.SingleKeygen,
            selectKeygenExecutor(TssAction.SingleKeygen, SigningLibType.GG20),
        )
        assertEquals(
            KeygenExecutor.SingleKeygen,
            selectKeygenExecutor(TssAction.SingleKeygen, SigningLibType.KeyImport),
        )
    }

    // -- Cross-table consistency --

    @Test
    fun `every reshare dispatch is exhaustively covered`() {
        SigningLibType.entries.forEach { libType ->
            val executor = selectKeygenExecutor(TssAction.ReShare, libType)
            // Reshare must NEVER land on the KeyImport-keygen executor (it errors), and must
            // NEVER land on SingleKeygen (that's MLDSA, a separate flow). Use JUnit
            // `assertNotEquals` rather than Kotlin's `assert`, which is a no-op when the JVM is
            // launched without `-ea` and would silently let regressions through here.
            assertNotEquals(
                KeygenExecutor.KeyImportKeygen,
                executor,
                "ReShare on $libType must not route to the KeyImport executor",
            )
            assertNotEquals(
                KeygenExecutor.SingleKeygen,
                executor,
                "ReShare on $libType must not route to the SingleKeygen executor",
            )
        }
    }
}

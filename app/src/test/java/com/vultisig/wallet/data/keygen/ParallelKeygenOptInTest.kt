package com.vultisig.wallet.data.keygen

import com.vultisig.wallet.data.models.TssAction
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

/**
 * Pins the [resolveParallelKeygenOptIn] matrix per action.
 *
 * Reshare and key-import trust the QR opt-in EXCLUSIVELY (a joiner must follow the initiator's
 * relay namespaces — OR-ing in a local flag would desync them). Keygen, migrate, and single-keygen
 * keep the OR fallback because the FastVault server's `joinBatchKeygen` is gated on the local
 * `isTssBatchEnabled` flag and the QR never carries the flag for them.
 *
 * The KEYGEN row is the regression guard: routing KEYGEN through `qrIsTssBatch` exclusively makes
 * the app run the legacy path while the server batches on `p-ecdsa` / `p-eddsa`, and the ceremony
 * deadlocks on mismatched relay channels.
 */
class ParallelKeygenOptInTest {

    // -- KEYGEN: OR fallback (regression guard) --

    @Test
    fun `KEYGEN follows the local flag when the QR did not opt in`() {
        // The exclusive (buggy) form would return false here and deadlock against the batched
        // server.
        assertTrue(
            resolveParallelKeygenOptIn(
                action = TssAction.KEYGEN,
                qrIsTssBatch = false,
                isTssBatchFeatureEnabled = true,
            )
        )
    }

    @Test
    fun `KEYGEN stays legacy when neither the flag nor the QR opted in`() {
        assertFalse(
            resolveParallelKeygenOptIn(
                action = TssAction.KEYGEN,
                qrIsTssBatch = false,
                isTssBatchFeatureEnabled = false,
            )
        )
    }

    @Test
    fun `KEYGEN follows the QR opt-in even with the flag off`() {
        assertTrue(
            resolveParallelKeygenOptIn(
                action = TssAction.KEYGEN,
                qrIsTssBatch = true,
                isTssBatchFeatureEnabled = false,
            )
        )
    }

    // -- ReShare / KeyImport: QR-exclusive --

    @Test
    fun `ReShare ignores the local flag and follows the QR exclusively`() {
        assertFalse(
            resolveParallelKeygenOptIn(
                action = TssAction.ReShare,
                qrIsTssBatch = false,
                isTssBatchFeatureEnabled = true,
            )
        )
        assertTrue(
            resolveParallelKeygenOptIn(
                action = TssAction.ReShare,
                qrIsTssBatch = true,
                isTssBatchFeatureEnabled = false,
            )
        )
    }

    @Test
    fun `KeyImport ignores the local flag and follows the QR exclusively`() {
        assertFalse(
            resolveParallelKeygenOptIn(
                action = TssAction.KeyImport,
                qrIsTssBatch = false,
                isTssBatchFeatureEnabled = true,
            )
        )
        assertTrue(
            resolveParallelKeygenOptIn(
                action = TssAction.KeyImport,
                qrIsTssBatch = true,
                isTssBatchFeatureEnabled = false,
            )
        )
    }

    // -- Migrate / SingleKeygen: OR fallback --

    @Test
    fun `Migrate follows the local flag when the QR did not opt in`() {
        assertTrue(
            resolveParallelKeygenOptIn(
                action = TssAction.Migrate,
                qrIsTssBatch = false,
                isTssBatchFeatureEnabled = true,
            )
        )
    }

    @Test
    fun `SingleKeygen follows the local flag when the QR did not opt in`() {
        assertTrue(
            resolveParallelKeygenOptIn(
                action = TssAction.SingleKeygen,
                qrIsTssBatch = false,
                isTssBatchFeatureEnabled = true,
            )
        )
    }

    @Test
    fun `with both inputs off, no action opts into the batched path`() {
        TssAction.entries.forEach { action ->
            assertFalse(
                resolveParallelKeygenOptIn(
                    action = action,
                    qrIsTssBatch = false,
                    isTssBatchFeatureEnabled = false,
                ),
                "$action must stay on the legacy path when nothing opted in",
            )
        }
    }
}

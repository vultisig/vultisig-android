package com.vultisig.wallet.data.keygen

import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import org.junit.jupiter.api.Test

/**
 * Verifies that the relay message IDs used by Android batch reshare match the server's
 * `ProcessBatchReshare` protocol prefixes — the same `p-ecdsa` / `p-eddsa` constants iOS PR #4139
 * and Windows PR #3753 already established as the cross-platform wire contract.
 *
 * The constants under test are the SAME `internal const val` declarations the production code
 * imports from `KeygenExecution.kt`. A previous version of this test redeclared the strings locally
 * and asserted them against themselves — a tautology that would have passed if someone accidentally
 * renamed the production constants. Now we hold both ends together: any rename in production breaks
 * this file, the assertion below pins the exact wire string, and the
 * `KeygenRouting.from(setupMessageId = ROOT_ECDSA_MESSAGE_ID, ...)` factories are exercised on the
 * same constants `KeygenViewModel.startKeygenDkls` uses at runtime.
 *
 * Reshare differs from keygen on a critical point: each protocol creates its own setup message, so
 * **both** setup AND exchange must be namespaced (keygen shares the DKLS setup namespace, but
 * reshare cannot). These tests pin that down so future refactors don't accidentally re-share the
 * namespaces and silently break joiner setup downloads.
 */
class ReshareRoutingServerMatchTest {

    // -- Wire-string pin: production constants must match the server contract verbatim --

    @Test
    fun `ROOT_ECDSA_MESSAGE_ID is exactly "p-ecdsa" — wire contract with server`() {
        assertEquals("p-ecdsa", ROOT_ECDSA_MESSAGE_ID)
    }

    @Test
    fun `ROOT_EDDSA_MESSAGE_ID is exactly "p-eddsa" — wire contract with server`() {
        assertEquals("p-eddsa", ROOT_EDDSA_MESSAGE_ID)
    }

    @Test
    fun `ROOT_MLDSA_EXCHANGE_MESSAGE_ID is exactly "p-mldsa"`() {
        assertEquals("p-mldsa", ROOT_MLDSA_EXCHANGE_MESSAGE_ID)
    }

    @Test
    fun `ROOT_MLDSA_SETUP_MESSAGE_ID is exactly "p-mldsa-setup"`() {
        assertEquals("p-mldsa-setup", ROOT_MLDSA_SETUP_MESSAGE_ID)
    }

    @Test
    fun `ROOT_ECDSA_KEY_IMPORT_MESSAGE_ID is exactly "ecdsa_key_import"`() {
        assertEquals("ecdsa_key_import", ROOT_ECDSA_KEY_IMPORT_MESSAGE_ID)
    }

    @Test
    fun `ROOT_EDDSA_KEY_IMPORT_MESSAGE_ID is exactly "eddsa_key_import"`() {
        assertEquals("eddsa_key_import", ROOT_EDDSA_KEY_IMPORT_MESSAGE_ID)
    }

    // -- ECDSA reshare routing --

    @Test
    fun `ECDSA reshare routing surfaces production p-ecdsa as exchange ID`() {
        val routing =
            KeygenRouting.from(
                setupMessageId = ROOT_ECDSA_MESSAGE_ID,
                exchangeMessageId = ROOT_ECDSA_MESSAGE_ID,
            )

        assertEquals("p-ecdsa", routing.exchangeMessageId)
    }

    @Test
    fun `ECDSA reshare routing surfaces production p-ecdsa as setup ID — unlike keygen`() {
        val routing =
            KeygenRouting.from(
                setupMessageId = ROOT_ECDSA_MESSAGE_ID,
                exchangeMessageId = ROOT_ECDSA_MESSAGE_ID,
            )

        assertEquals("p-ecdsa", routing.setupMessageId)
    }

    @Test
    fun `ECDSA reshare setup and exchange route through the same namespace`() {
        val routing =
            KeygenRouting.from(
                setupMessageId = ROOT_ECDSA_MESSAGE_ID,
                exchangeMessageId = ROOT_ECDSA_MESSAGE_ID,
            )

        assertEquals(routing.setupMessageId, routing.exchangeMessageId)
    }

    // -- EdDSA reshare routing --

    @Test
    fun `EdDSA reshare routing surfaces production p-eddsa as exchange ID`() {
        val routing =
            KeygenRouting.from(
                setupMessageId = ROOT_EDDSA_MESSAGE_ID,
                exchangeMessageId = ROOT_EDDSA_MESSAGE_ID,
            )

        assertEquals("p-eddsa", routing.exchangeMessageId)
    }

    @Test
    fun `EdDSA reshare routing surfaces production p-eddsa as setup ID`() {
        val routing =
            KeygenRouting.from(
                setupMessageId = ROOT_EDDSA_MESSAGE_ID,
                exchangeMessageId = ROOT_EDDSA_MESSAGE_ID,
            )

        assertEquals("p-eddsa", routing.setupMessageId)
    }

    @Test
    fun `EdDSA reshare setup and exchange route through the same namespace`() {
        val routing =
            KeygenRouting.from(
                setupMessageId = ROOT_EDDSA_MESSAGE_ID,
                exchangeMessageId = ROOT_EDDSA_MESSAGE_ID,
            )

        assertEquals(routing.setupMessageId, routing.exchangeMessageId)
    }

    // -- Cross-protocol isolation --

    @Test
    fun `ECDSA and EdDSA reshare use different namespaces so traffic does not collide`() {
        val ecdsa =
            KeygenRouting.from(
                setupMessageId = ROOT_ECDSA_MESSAGE_ID,
                exchangeMessageId = ROOT_ECDSA_MESSAGE_ID,
            )
        val eddsa =
            KeygenRouting.from(
                setupMessageId = ROOT_EDDSA_MESSAGE_ID,
                exchangeMessageId = ROOT_EDDSA_MESSAGE_ID,
            )

        assertNotEquals(ecdsa.exchangeMessageId, eddsa.exchangeMessageId)
        assertNotEquals(ecdsa.setupMessageId, eddsa.setupMessageId)
    }

    @Test
    fun `both reshare protocols produce non-null setup and exchange IDs`() {
        val ecdsa =
            KeygenRouting.from(
                setupMessageId = ROOT_ECDSA_MESSAGE_ID,
                exchangeMessageId = ROOT_ECDSA_MESSAGE_ID,
            )
        val eddsa =
            KeygenRouting.from(
                setupMessageId = ROOT_EDDSA_MESSAGE_ID,
                exchangeMessageId = ROOT_EDDSA_MESSAGE_ID,
            )

        assertNotNull(ecdsa.setupMessageId)
        assertNotNull(ecdsa.exchangeMessageId)
        assertNotNull(eddsa.setupMessageId)
        assertNotNull(eddsa.exchangeMessageId)
    }

    // -- Legacy fallback (sequential reshare) --

    @Test
    fun `default routing maps to legacy reshare — both IDs null so existing peers stay compatible`() {
        val routing = KeygenRouting.from()

        assertNull(routing.setupMessageId)
        assertNull(routing.exchangeMessageId)
    }
}

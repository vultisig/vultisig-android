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
 * Reshare differs from keygen on a critical point: each protocol creates its own setup message, so
 * **both** setup AND exchange must be namespaced (keygen shares the DKLS setup namespace, but
 * reshare cannot). These tests pin that down so future refactors don't accidentally re-share the
 * namespaces and silently break joiner setup downloads.
 */
class ReshareRoutingServerMatchTest {

    // -- ECDSA reshare routing --

    @Test
    fun `ECDSA reshare uses p-ecdsa as exchange message ID`() {
        val routing =
            KeygenRouting.from(
                setupMessageId = ECDSA_MESSAGE_ID,
                exchangeMessageId = ECDSA_MESSAGE_ID,
            )

        assertEquals("p-ecdsa", routing.exchangeMessageId)
    }

    @Test
    fun `ECDSA reshare uses p-ecdsa as setup message ID — unlike keygen which shares DKLS setup`() {
        val routing =
            KeygenRouting.from(
                setupMessageId = ECDSA_MESSAGE_ID,
                exchangeMessageId = ECDSA_MESSAGE_ID,
            )

        assertEquals("p-ecdsa", routing.setupMessageId)
    }

    @Test
    fun `ECDSA reshare setup and exchange route through the same namespace`() {
        val routing =
            KeygenRouting.from(
                setupMessageId = ECDSA_MESSAGE_ID,
                exchangeMessageId = ECDSA_MESSAGE_ID,
            )

        assertEquals(routing.setupMessageId, routing.exchangeMessageId)
    }

    // -- EdDSA reshare routing --

    @Test
    fun `EdDSA reshare uses p-eddsa as exchange message ID`() {
        val routing =
            KeygenRouting.from(
                setupMessageId = EDDSA_MESSAGE_ID,
                exchangeMessageId = EDDSA_MESSAGE_ID,
            )

        assertEquals("p-eddsa", routing.exchangeMessageId)
    }

    @Test
    fun `EdDSA reshare uses p-eddsa as setup message ID`() {
        val routing =
            KeygenRouting.from(
                setupMessageId = EDDSA_MESSAGE_ID,
                exchangeMessageId = EDDSA_MESSAGE_ID,
            )

        assertEquals("p-eddsa", routing.setupMessageId)
    }

    @Test
    fun `EdDSA reshare setup and exchange route through the same namespace`() {
        val routing =
            KeygenRouting.from(
                setupMessageId = EDDSA_MESSAGE_ID,
                exchangeMessageId = EDDSA_MESSAGE_ID,
            )

        assertEquals(routing.setupMessageId, routing.exchangeMessageId)
    }

    // -- Cross-protocol isolation --

    @Test
    fun `ECDSA and EdDSA reshare use different namespaces so traffic does not collide`() {
        val ecdsa =
            KeygenRouting.from(
                setupMessageId = ECDSA_MESSAGE_ID,
                exchangeMessageId = ECDSA_MESSAGE_ID,
            )
        val eddsa =
            KeygenRouting.from(
                setupMessageId = EDDSA_MESSAGE_ID,
                exchangeMessageId = EDDSA_MESSAGE_ID,
            )

        assertNotEquals(ecdsa.exchangeMessageId, eddsa.exchangeMessageId)
        assertNotEquals(ecdsa.setupMessageId, eddsa.setupMessageId)
    }

    @Test
    fun `both reshare protocols produce non-null setup and exchange IDs`() {
        val ecdsa =
            KeygenRouting.from(
                setupMessageId = ECDSA_MESSAGE_ID,
                exchangeMessageId = ECDSA_MESSAGE_ID,
            )
        val eddsa =
            KeygenRouting.from(
                setupMessageId = EDDSA_MESSAGE_ID,
                exchangeMessageId = EDDSA_MESSAGE_ID,
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

    private companion object {
        // These values MUST match the private companion constants in KeygenViewModel and the
        // server's ProcessBatchReshare handler. They are the same identifiers used by batch
        // keygen — the server reuses its parallel-protocol prefixes for reshare too.
        const val ECDSA_MESSAGE_ID = "p-ecdsa"
        const val EDDSA_MESSAGE_ID = "p-eddsa"
    }
}

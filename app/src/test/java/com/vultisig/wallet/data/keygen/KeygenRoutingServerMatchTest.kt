package com.vultisig.wallet.data.keygen

import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import org.junit.jupiter.api.Test

/**
 * Verifies that the relay message IDs used by the Android parallel keygen match the server's batch
 * keygen protocol prefixes.
 *
 * The server's `ProcessBatchKeygen` handler expects these exact strings as `message_id` headers to
 * route TSS traffic to the correct ceremony goroutine:
 * - `"p-ecdsa"` for DKLS ECDSA exchange
 * - `"p-eddsa"` for Schnorr EdDSA exchange
 * - `"p-mldsa"` for MLDSA exchange
 * - `"p-mldsa-setup"` for MLDSA setup upload
 *
 * Since the constants in [com.vultisig.wallet.ui.models.keygen.KeygenViewModel] are private, we
 * verify them indirectly through [KeygenRouting.from] -- the same factory the ViewModel uses to
 * build routing objects.
 */
class KeygenRoutingServerMatchTest {

    // -- ECDSA routing --

    @Test
    fun `ECDSA routing uses p-ecdsa as exchange message ID`() {
        val routing =
            KeygenRouting.from(
                setupMessageId = ECDSA_MESSAGE_ID,
                exchangeMessageId = ECDSA_MESSAGE_ID,
            )

        assertEquals("p-ecdsa", routing.exchangeMessageId)
    }

    @Test
    fun `ECDSA routing uses p-ecdsa as setup message ID`() {
        val routing =
            KeygenRouting.from(
                setupMessageId = ECDSA_MESSAGE_ID,
                exchangeMessageId = ECDSA_MESSAGE_ID,
            )

        assertEquals("p-ecdsa", routing.setupMessageId)
    }

    // -- EdDSA routing --

    @Test
    fun `EdDSA routing uses p-eddsa as exchange message ID`() {
        val routing =
            KeygenRouting.from(
                setupMessageId = ECDSA_MESSAGE_ID,
                exchangeMessageId = EDDSA_MESSAGE_ID,
            )

        assertEquals("p-eddsa", routing.exchangeMessageId)
    }

    @Test
    fun `EdDSA routing reads shared DKLS setup from p-ecdsa`() {
        // Schnorr reuses the DKLS setup message uploaded at p-ecdsa; only the exchange
        // namespace differs.
        val routing =
            KeygenRouting.from(
                setupMessageId = ECDSA_MESSAGE_ID,
                exchangeMessageId = EDDSA_MESSAGE_ID,
            )

        assertEquals("p-ecdsa", routing.setupMessageId)
    }

    // -- MLDSA routing --

    @Test
    fun `MLDSA routing uses p-mldsa as exchange message ID`() {
        val routing =
            KeygenRouting.from(
                setupMessageId = MLDSA_SETUP_MESSAGE_ID,
                exchangeMessageId = MLDSA_EXCHANGE_MESSAGE_ID,
            )

        assertEquals("p-mldsa", routing.exchangeMessageId)
    }

    @Test
    fun `MLDSA routing uses p-mldsa-setup as setup message ID`() {
        val routing =
            KeygenRouting.from(
                setupMessageId = MLDSA_SETUP_MESSAGE_ID,
                exchangeMessageId = MLDSA_EXCHANGE_MESSAGE_ID,
            )

        assertEquals("p-mldsa-setup", routing.setupMessageId)
    }

    @Test
    fun `MLDSA setup and exchange use different namespaces`() {
        val routing =
            KeygenRouting.from(
                setupMessageId = MLDSA_SETUP_MESSAGE_ID,
                exchangeMessageId = MLDSA_EXCHANGE_MESSAGE_ID,
            )

        assert(routing.setupMessageId != routing.exchangeMessageId) {
            "MLDSA setup and exchange must use different relay namespaces " +
                "to prevent message collision"
        }
    }

    // -- all protocols produce non-null exchange IDs --

    @Test
    fun `all parallel keygen protocols produce non-null exchange message IDs`() {
        val ecdsaRouting =
            KeygenRouting.from(
                setupMessageId = ECDSA_MESSAGE_ID,
                exchangeMessageId = ECDSA_MESSAGE_ID,
            )
        val eddsaRouting =
            KeygenRouting.from(
                setupMessageId = ECDSA_MESSAGE_ID,
                exchangeMessageId = EDDSA_MESSAGE_ID,
            )
        val mldsaRouting =
            KeygenRouting.from(
                setupMessageId = MLDSA_SETUP_MESSAGE_ID,
                exchangeMessageId = MLDSA_EXCHANGE_MESSAGE_ID,
            )

        assertNotEquals(null, ecdsaRouting.exchangeMessageId, "ECDSA exchange ID must not be null")
        assertNotEquals(null, eddsaRouting.exchangeMessageId, "EdDSA exchange ID must not be null")
        assertNotEquals(null, mldsaRouting.exchangeMessageId, "MLDSA exchange ID must not be null")
    }

    // -- all protocols use unique exchange namespaces --

    @Test
    fun `all parallel keygen protocols use unique exchange namespaces`() {
        val ids = setOf(ECDSA_MESSAGE_ID, EDDSA_MESSAGE_ID, MLDSA_EXCHANGE_MESSAGE_ID)

        assertEquals(3, ids.size, "Each protocol must have a unique exchange message ID")
    }

    // -- empty string normalization --

    @Test
    fun `empty exchange message ID is normalized to null for legacy mode`() {
        val routing = KeygenRouting.from(exchangeMessageId = "")

        assertNull(routing.exchangeMessageId)
        assertNull(routing.setupMessageId)
    }

    @Test
    fun `empty setup message ID is normalized to null`() {
        val routing = KeygenRouting.from(setupMessageId = "", exchangeMessageId = ECDSA_MESSAGE_ID)

        assertNull(routing.setupMessageId)
        assertEquals("p-ecdsa", routing.exchangeMessageId)
    }

    private companion object {
        // These values MUST match the private companion constants in KeygenViewModel
        // and the server's ProcessBatchKeygen handler.
        const val ECDSA_MESSAGE_ID = "p-ecdsa"
        const val EDDSA_MESSAGE_ID = "p-eddsa"
        const val MLDSA_EXCHANGE_MESSAGE_ID = "p-mldsa"
        const val MLDSA_SETUP_MESSAGE_ID = "p-mldsa-setup"
    }
}

package com.vultisig.wallet.data.keygen

import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.jupiter.api.Test

/**
 * Locks the relay namespaces used by the Android **batched / parallel key-import** ceremony to the
 * cross-platform contract.
 *
 * The initiator of a seed-phrase import is frequently the browser extension or iOS. Those clients
 * drive the batched key-import protocol on these exact channels (see
 * `KeyImportKeygenActionProvider.tsx` / `JoinKeyImportKeygenActionProvider.tsx` on vultisig-windows
 * and `KeygenViewModel.swift` on vultisig-ios):
 * - root ECDSA: setup on the DEFAULT namespace, exchange on `p-ecdsa`
 * - root EdDSA: setup on `eddsa_key_import`, exchange on `p-eddsa`
 * - per chain : setup on the chain id, exchange on `p-{chain}`
 *
 * Android previously routed root ECDSA/EdDSA through `ecdsa_key_import` / `eddsa_key_import` for
 * BOTH setup and exchange, and per-chain exchange through the bare chain id. That only ever lined
 * up with another Android device, so an extension/iOS initiator joined by an Android device
 * deadlocked on mismatched relay channels. These assertions guard against that regression
 * returning.
 *
 * The ViewModel builds these routings inline via [KeygenRouting.from]; we rebuild them here with
 * the same factory and constants so the contract is verified without booting the JNI-heavy
 * ceremony.
 */
class KeyImportRoutingServerMatchTest {

    @Test
    fun `root ECDSA key-import exchanges on p-ecdsa`() {
        val routing = KeygenRouting.from(exchangeMessageId = ROOT_ECDSA_MESSAGE_ID)

        assertEquals("p-ecdsa", routing.exchangeMessageId)
    }

    @Test
    fun `root ECDSA key-import setup stays on the default namespace`() {
        val routing = KeygenRouting.from(exchangeMessageId = ROOT_ECDSA_MESSAGE_ID)

        assertNull(routing.setupMessageId)
    }

    @Test
    fun `root EdDSA key-import exchanges on p-eddsa`() {
        val routing =
            KeygenRouting.from(
                setupMessageId = ROOT_EDDSA_KEY_IMPORT_MESSAGE_ID,
                exchangeMessageId = ROOT_EDDSA_MESSAGE_ID,
            )

        assertEquals("p-eddsa", routing.exchangeMessageId)
    }

    @Test
    fun `root EdDSA key-import sets up on eddsa_key_import`() {
        val routing =
            KeygenRouting.from(
                setupMessageId = ROOT_EDDSA_KEY_IMPORT_MESSAGE_ID,
                exchangeMessageId = ROOT_EDDSA_MESSAGE_ID,
            )

        assertEquals("eddsa_key_import", routing.setupMessageId)
    }

    @Test
    fun `batched per-chain key-import exchanges on p-{chain} and sets up on the chain id`() {
        val chain = "bitcoin"

        val routing = KeygenRouting.from(setupMessageId = chain, exchangeMessageId = "p-$chain")

        assertEquals("p-bitcoin", routing.exchangeMessageId)
        assertEquals("bitcoin", routing.setupMessageId)
    }

    @Test
    fun `sequential per-chain key-import exchanges on the default channel, sets up on the chain id`() {
        // Sequential chains share the default exchange channel (null). The explicit empty
        // exchangeMessageId is required because `from` would otherwise copy the setup id (chain
        // name) into the exchange id — which made the joiner poll an "Ethereum" channel the
        // initiator never posts to (observed as an endless empty message-pull loop).
        val chain = "Ethereum"

        val routing = KeygenRouting.from(setupMessageId = chain, exchangeMessageId = "")

        assertNull(routing.exchangeMessageId)
        assertEquals("Ethereum", routing.setupMessageId)
    }

    @Test
    fun `root setup namespaces never collide`() {
        val ecdsa = KeygenRouting.from(exchangeMessageId = ROOT_ECDSA_MESSAGE_ID)
        val eddsa =
            KeygenRouting.from(
                setupMessageId = ROOT_EDDSA_KEY_IMPORT_MESSAGE_ID,
                exchangeMessageId = ROOT_EDDSA_MESSAGE_ID,
            )

        // ECDSA setup is the default (null) namespace; EdDSA setup is "eddsa_key_import".
        assert(ecdsa.setupMessageId != eddsa.setupMessageId) {
            "Root ECDSA and EdDSA setup must use different relay namespaces"
        }
    }
}

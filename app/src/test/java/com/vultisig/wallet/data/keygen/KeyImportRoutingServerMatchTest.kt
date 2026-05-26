package com.vultisig.wallet.data.keygen

import com.vultisig.wallet.data.models.Chain
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
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
 * The root routings are built via [KeygenRouting.from] with the same constants the ViewModel uses;
 * the per-chain routings call [keyImportChainRouting] — the exact helper the ViewModel runs — keyed
 * off `chain.raw`, so dropping the load-bearing empty sequential exchange id (or lowercasing the
 * namespace) breaks this file instead of slipping through. All verified without booting the
 * JNI-heavy ceremony.
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
    fun `batched per-chain key-import exchanges on p-{chain raw} and sets up on the chain id`() {
        // Built through the SAME helper the ViewModel uses, keyed off chain.raw — so the test pins
        // the channel production actually emits ("Bitcoin", not a lowercased literal).
        val routing = keyImportChainRouting(Chain.Bitcoin.raw, useParallelPath = true)

        assertEquals("p-Bitcoin", routing.exchangeMessageId)
        assertEquals("Bitcoin", routing.setupMessageId)
    }

    @Test
    fun `sequential per-chain key-import exchanges on the default channel, sets up on the chain id`() {
        // Sequential chains share the default exchange channel (null). The explicit empty exchange
        // id
        // inside keyImportChainRouting is required because `from` would otherwise copy the setup id
        // (chain name) into the exchange id — which made the joiner poll an "Ethereum" channel the
        // initiator never posts to (observed as an endless empty message-pull loop).
        val routing = keyImportChainRouting(Chain.Ethereum.raw, useParallelPath = false)

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
        assertNotEquals(
            ecdsa.setupMessageId,
            eddsa.setupMessageId,
            "Root ECDSA and EdDSA setup must use different relay namespaces",
        )
    }
}

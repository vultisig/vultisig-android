package com.vultisig.wallet.data.chains.helpers

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import wallet.core.jni.proto.Solana

class SolanaSwapTest {

    /**
     * Builds a v0 [Solana.RawMessage] with [staticKeys] static account keys and one address-table
     * lookup referencing [writableRefs] writable and [readonlyRefs] readonly indexes.
     */
    private fun v0Message(
        staticKeys: Int,
        writableRefs: Int,
        readonlyRefs: Int,
    ): Solana.RawMessage {
        val lookup =
            Solana.RawMessage.MessageAddressTableLookup.newBuilder()
                .setAccountKey("lookupTable")
                .addAllWritableIndexes((0 until writableRefs).toList())
                .addAllReadonlyIndexes((0 until readonlyRefs).toList())
                .build()
        val v0 =
            Solana.RawMessage.MessageV0.newBuilder()
                .addAllAccountKeys((0 until staticKeys).map { "key$it" })
                .addAddressTableLookups(lookup)
                .build()
        return Solana.RawMessage.newBuilder().setV0(v0).build()
    }

    private fun legacyMessage(staticKeys: Int): Solana.RawMessage {
        val legacy =
            Solana.RawMessage.MessageLegacy.newBuilder()
                .addAllAccountKeys((0 until staticKeys).map { "key$it" })
                .build()
        return Solana.RawMessage.newBuilder().setLegacy(legacy).build()
    }

    @Test
    fun `v0 lock count sums static keys and both lookup index kinds`() {
        // The #5131 repro: 16 static keys + 35 writable-refs + 15 readonly-refs = 66.
        val message = v0Message(staticKeys = 16, writableRefs = 35, readonlyRefs = 15)
        assertEquals(66, SolanaSwap.countAccountLocks(message))
    }

    @Test
    fun `v0 tx above the cap is flagged as exceeding the limit`() {
        val message = v0Message(staticKeys = 16, writableRefs = 35, readonlyRefs = 15)
        assertTrue(SolanaSwap.countAccountLocks(message) > SolanaSwap.MAX_TX_ACCOUNT_LOCKS)
    }

    @Test
    fun `v0 tx at exactly the cap is not flagged`() {
        val message = v0Message(staticKeys = 16, writableRefs = 33, readonlyRefs = 15)
        assertEquals(64, SolanaSwap.countAccountLocks(message))
        assertFalse(SolanaSwap.countAccountLocks(message) > SolanaSwap.MAX_TX_ACCOUNT_LOCKS)
    }

    @Test
    fun `legacy tx locks only its static account keys`() {
        val message = legacyMessage(staticKeys = 20)
        assertEquals(20, SolanaSwap.countAccountLocks(message))
        assertFalse(SolanaSwap.countAccountLocks(message) > SolanaSwap.MAX_TX_ACCOUNT_LOCKS)
    }
}

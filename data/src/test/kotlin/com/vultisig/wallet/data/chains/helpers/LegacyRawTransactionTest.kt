package com.vultisig.wallet.data.chains.helpers

import java.security.MessageDigest
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import org.junit.jupiter.api.Test

internal class LegacyRawTransactionTest {

    // Bitcoin's genesis coinbase: the legacy layout DOGE and DASH share, with a well-known txid.
    private val genesisBody =
        "010000000000000000000000000000000000000000000000000000000000000000ffffffff4d04ffff00" +
            "1d0104455468652054696d65732030332f4a616e2f32303039204368616e63656c6c6f72206f6e2062" +
            "72696e6b206f66207365636f6e64206261696c6f757420666f722062616e6b73ffffffff0100f2052a" +
            "01000000434104678afdb0fe5548271967f1a67130b7105cd6a828e03909a67962e0ea1f61deb649f6" +
            "bc3f4cef38c4f35504e51ec112de5c384df7ba0b8d578a4c702b6bf11d5fac00000000"
    private val genesis = "01000000$genesisBody"

    @Test
    fun `hashes a legacy transaction to its txid and reads its outputs`() {
        val tx = assertNotNull(LegacyRawTransaction.parseOrNull(genesis))

        assertEquals("4a5e1e4baab89f3a32518a88c31bc87f618f76673e2cc77ab2127b7afdeda33b", tx.txid)
        assertEquals(1, tx.outputs.size)
        assertEquals(5_000_000_000L, tx.outputs[0].value)
        assertEquals(67, tx.outputs[0].script.size)
    }

    @Test
    fun `accepts a 0x prefix`() {
        assertNotNull(LegacyRawTransaction.parseOrNull("0x$genesis"))
    }

    @Test
    fun `reads a Dash special transaction and hashes its payload too`() {
        // Version 3, type 1 (DIP-2), with a 4-byte payload after the lock time.
        val hex = "03000100$genesisBody" + "04deadbeef"
        val tx = assertNotNull(LegacyRawTransaction.parseOrNull(hex))

        assertEquals(doubleSha256Txid(hex), tx.txid)
        assertEquals(5_000_000_000L, tx.outputs.single().value)
    }

    @Test
    fun `reads a Dash asset unlock transaction with no inputs`() {
        val output = "00f2052a01000000" + "1976a914" + "11".repeat(20) + "88ac"
        val hex = "03000900" + "00" + "01" + output + "00000000" + "0101"
        val tx = assertNotNull(LegacyRawTransaction.parseOrNull(hex))

        assertEquals(5_000_000_000L, tx.outputs.single().value)
        assertEquals(25, tx.outputs.single().script.size)
    }

    @Test
    fun `rejects counts larger than the bytes left`() {
        assertNull(LegacyRawTransaction.parseOrNull("01000000" + "fe00000080" + "00".repeat(64)))
        assertNull(LegacyRawTransaction.parseOrNull("01000000" + "ffffffffffffffffff"))
    }

    @Test
    fun `rejects truncated or non-hex input`() {
        assertNull(LegacyRawTransaction.parseOrNull(genesis.dropLast(10)))
        assertNull(LegacyRawTransaction.parseOrNull("not a transaction"))
    }

    private fun doubleSha256Txid(hex: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(digest.digest(hex.hexToByteArray())).reversedArray().toHexString()
    }
}

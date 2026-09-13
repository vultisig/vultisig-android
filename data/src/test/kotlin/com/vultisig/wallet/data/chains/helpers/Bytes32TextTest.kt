package com.vultisig.wallet.data.chains.helpers

import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.jupiter.api.Test

internal class Bytes32TextTest {

    private val mkrWord = "4d4b52" + "0".repeat(58)

    @Test
    fun `reads a zero-padded bytes32 word as text`() {
        assertEquals("MKR", mkrWord.hexToBytes().bytes32TextOrNull())
    }

    @Test
    fun `rejects a word that is not 32 bytes`() {
        assertNull("4d4b52".hexToBytes().bytes32TextOrNull())
        assertNull((mkrWord + "00").hexToBytes().bytes32TextOrNull())
    }

    @Test
    fun `rejects an all-zero word`() {
        assertNull(ByteArray(32).bytes32TextOrNull())
    }

    @Test
    fun `rejects a word whose bytes are not printable ASCII`() {
        assertNull("ff".repeat(32).hexToBytes().bytes32TextOrNull())
        // A control character before the terminator disqualifies the whole word.
        assertNull(("4d4b0752" + "0".repeat(56)).hexToBytes().bytes32TextOrNull())
    }

    // decodeBytes32HexOrSelf is applied directly to aggregator token symbols (1inch), which is the
    // path that feeds the swap "To" selector — there the value already arrives as the bare bytes32
    // hex, not a full ABI eth_call result (issue #4873).

    @Test
    fun `decodes a bare bytes32-as-hex symbol back to text`() {
        assertEquals("MKR", mkrWord.decodeBytes32HexOrSelf())
    }

    @Test
    fun `leaves a normal aggregator ticker untouched`() {
        assertEquals("MKR", "MKR".decodeBytes32HexOrSelf())
        assertEquals("USDC", "USDC".decodeBytes32HexOrSelf())
    }

    @Test
    fun `leaves a 64-char value that is not printable bytes32 text untouched`() {
        // A genuine 64-hex-char string whose decoded bytes are non-printable must survive as-is.
        val raw = "ff".repeat(32)
        assertEquals(raw, raw.decodeBytes32HexOrSelf())
    }

    @Test
    fun `leaves a 64-char value that is not hex untouched`() {
        val raw = "g".repeat(64)
        assertEquals(raw, raw.decodeBytes32HexOrSelf())
    }

    private fun String.hexToBytes(): ByteArray =
        chunked(2).map { it.toInt(16).toByte() }.toByteArray()
}

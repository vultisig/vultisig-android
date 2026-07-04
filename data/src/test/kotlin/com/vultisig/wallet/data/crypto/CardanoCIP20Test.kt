package com.vultisig.wallet.data.crypto

import com.vultisig.wallet.data.common.Utils
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Byte-parity fixtures shared with the iOS `CardanoCIP20Tests` and the SDK's
 * `buildCip20AuxData.test.ts`. The CIP-20 CBOR bytes and blake2b-256 aux hash MUST be identical
 * across iOS / Android / Extension or MPC co-signers disagree on the Cardano sighash. Any drift
 * here or in another platform's encoder breaks one of these tests.
 */
class CardanoCIP20Test {

    private fun ByteArray.hex(): String = joinToString("") { "%02x".format(it) }

    private fun String.utf8Hex(): String = toByteArray(Charsets.UTF_8).hex()

    // memoToChunks

    @Test
    fun `returns single chunk for short memo`() {
        assertEquals(listOf("hello world"), CardanoCIP20.memoToChunks("hello world"))
    }

    @Test
    fun `returns single empty chunk for empty input`() {
        assertEquals(listOf(""), CardanoCIP20.memoToChunks(""))
    }

    @Test
    fun `splits exactly on 64-byte boundary for ascii`() {
        val chunks = CardanoCIP20.memoToChunks("a".repeat(65))
        assertEquals(2, chunks.size)
        assertEquals(64, chunks[0].toByteArray(Charsets.UTF_8).size)
        assertEquals(1, chunks[1].toByteArray(Charsets.UTF_8).size)
    }

    @Test
    fun `does not split a 64-byte memo`() {
        val memo64 = "x".repeat(64)
        assertEquals(listOf(memo64), CardanoCIP20.memoToChunks(memo64))
    }

    @Test
    fun `does not tear a 4-byte codepoint straddling the boundary`() {
        // 63 ASCII 'a' + U+1F600 (4 bytes) + 'b'. A naive byte-cut would tear the emoji.
        val memo = "a".repeat(63) + "😀" + "b"
        val chunks = CardanoCIP20.memoToChunks(memo)
        assertEquals(memo, chunks.joinToString(""))
        assertFalse(chunks.joinToString("").contains("�"))
        assertTrue(chunks[0].toByteArray(Charsets.UTF_8).size <= 64)
        assertTrue(chunks[1].contains("😀"))
    }

    @Test
    fun `does not tear a 2-byte codepoint straddling the boundary`() {
        val memo = "a".repeat(63) + "ñ" + "c"
        val chunks = CardanoCIP20.memoToChunks(memo)
        assertEquals(memo, chunks.joinToString(""))
        assertFalse(chunks.joinToString("").contains("�"))
    }

    @Test
    fun `does not tear a 3-byte codepoint straddling the boundary`() {
        val memo = "a".repeat(62) + "日" + "d"
        val chunks = CardanoCIP20.memoToChunks(memo)
        assertEquals(memo, chunks.joinToString(""))
        assertFalse(chunks.joinToString("").contains("�"))
    }

    @Test
    fun `multi-byte memo round-trips through chunks`() {
        val memo = "a".repeat(63) + "😀" + "b"
        val chunks = CardanoCIP20.memoToChunks(memo)
        assertEquals(memo, chunks.joinToString(""))
        assertFalse(chunks.joinToString("").contains("�"))
        for (chunk in chunks) {
            assertTrue(chunk.toByteArray(Charsets.UTF_8).size <= 64)
        }
    }

    // buildAuxData byte parity

    @Test
    fun `buildAuxData produces pinned cbor for hello world`() {
        // a1 1902a2 a1 636d7367 81 6b <"hello world">
        val expected = "a11902a2a1636d7367816b" + "hello world".utf8Hex()
        assertEquals(expected, CardanoCIP20.buildAuxData("hello world").auxDataCbor.hex())
    }

    @Test
    fun `buildAuxData encodes label 674 and msg key`() {
        val hex = CardanoCIP20.buildAuxData("hello world").auxDataCbor.hex()
        assertTrue(hex.startsWith("a11902a2a1636d7367"))
    }

    @Test
    fun `buildAuxData chunks a 64-byte chunk head correctly`() {
        // A 65-byte ASCII memo -> two text chunks: one 64-byte (head 78 40) and one 1-byte.
        val hex = CardanoCIP20.buildAuxData("a".repeat(65)).auxDataCbor.hex()
        assertTrue(hex.contains("82")) // array(2) header
        assertTrue(hex.contains("7840" + "61".repeat(64))) // 64-byte text head + 64 'a'
    }

    @Test
    fun `buildAuxData empty memo encodes single empty chunk`() {
        // { 674: { "msg": [""] } } -> ... 81 60  (array(1), text(0))
        assertEquals("a11902a2a1636d7367" + "8160", CardanoCIP20.buildAuxData("").auxDataCbor.hex())
    }

    @Test
    fun `aux data hash is blake2b-256 of cbor`() {
        val aux = CardanoCIP20.buildAuxData("vultisig-test")
        assertEquals(32, aux.auxDataHash.size)
        assertEquals(Utils.blake2bHash(aux.auxDataCbor).hex(), aux.auxDataHash.hex())
        assertTrue(aux.auxDataHash.any { it.toInt() != 0 })
    }
}

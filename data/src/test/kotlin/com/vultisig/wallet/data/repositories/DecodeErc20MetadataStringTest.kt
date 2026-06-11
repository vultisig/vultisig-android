package com.vultisig.wallet.data.repositories

import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.jupiter.api.Test

internal class DecodeErc20MetadataStringTest {

    // Real eth_call responses captured from MKR on Arbitrum (0x2e9a…2879). MKR declares
    // name()/symbol() as bytes32; the bridged deployment returns them as an ABI string whose
    // content is the 64-char hex of the bytes32, right-padded with zeros (issue #4873).
    private val mkrSymbolResponse =
        "0x0000000000000000000000000000000000000000000000000000000000000020" +
            "0000000000000000000000000000000000000000000000000000000000000040" +
            "3464346235323030303030303030303030303030303030303030303030303030" +
            "3030303030303030303030303030303030303030303030303030303030303030"

    private val mkrNameResponse =
        "0x0000000000000000000000000000000000000000000000000000000000000020" +
            "0000000000000000000000000000000000000000000000000000000000000040" +
            "3464363136623635373230303030303030303030303030303030303030303030" +
            "3030303030303030303030303030303030303030303030303030303030303030"

    @Test
    fun `decodes a bytes32-as-hex-string symbol back to text`() {
        assertEquals("MKR", decodeErc20MetadataString(mkrSymbolResponse))
    }

    @Test
    fun `decodes a bytes32-as-hex-string name back to text`() {
        assertEquals("Maker", decodeErc20MetadataString(mkrNameResponse))
    }

    @Test
    fun `passes a normal ABI string symbol through unchanged`() {
        // Standard ERC-20 symbol() = "USDC": offset 0x20, length 0x04, data right-padded.
        val usdc =
            "0x0000000000000000000000000000000000000000000000000000000000000020" +
                "0000000000000000000000000000000000000000000000000000000000000004" +
                "5553444300000000000000000000000000000000000000000000000000000000"

        assertEquals("USDC", decodeErc20MetadataString(usdc))
    }

    @Test
    fun `does not mangle a short ticker whose letters happen to be hex digits`() {
        // "FACE" is valid hex, but as a 4-char string it is not a padded bytes32, so it must
        // survive untouched (guard: only 64-char hex values are treated as bytes32).
        val face =
            "0x0000000000000000000000000000000000000000000000000000000000000020" +
                "0000000000000000000000000000000000000000000000000000000000000004" +
                "4641434500000000000000000000000000000000000000000000000000000000"

        assertEquals("FACE", decodeErc20MetadataString(face))
    }

    @Test
    fun `returns null on malformed input`() {
        assertNull(decodeErc20MetadataString("0xdeadbeef"))
    }
}

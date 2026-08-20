@file:OptIn(ExperimentalStdlibApi::class)

package com.vultisig.wallet.data.crypto

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * Pins [Base58Codec] against real Solana addresses, since everything derived from it is compared
 * against an address that came off the wire.
 */
class Base58CodecTest {

    @Test
    fun `every well-known address survives a round trip as 32 bytes`() {
        listOf(
                "9ceRgz579BcfWogs3RE11FKNQaWW7Lmtnev3MXspxUjF",
                "ATokenGPvbdGVxr1b2hvZbsiqW5xWH25efTNsLJA8knL",
                "TokenkegQfeZyiNwAJbNbGKPFXCWuBvf9Ss623VQ5DA",
                "FarmsPZpWu9i7Kky8tPN37rs2TpmMrAZrC7S7vJa91Hr",
                "So11111111111111111111111111111111111111112",
            )
            .forEach { address ->
                val decoded = Base58Codec.decode(address)
                assertEquals(32, decoded?.size, address)
                assertEquals(address, Base58Codec.encode(decoded!!), address)
            }
    }

    @Test
    fun `leading zero bytes survive, which is what keeps a key 32 bytes wide`() {
        // The System program is 32 zero bytes, so every character of its address is the
        // leading-zero
        // marker. Dropping them would hand a 0-byte key to a derivation that requires 32.
        val system = "11111111111111111111111111111111"
        val decoded = Base58Codec.decode(system)
        assertArrayEquals(ByteArray(32), decoded)
        assertEquals(system, Base58Codec.encode(ByteArray(32)))

        // A single leading zero in front of a value whose high bit is set exercises both the
        // leading-zero restore and the sign byte BigInteger prepends.
        val mixed = byteArrayOf(0, 0xff.toByte(), 0x01)
        assertArrayEquals(mixed, Base58Codec.decode(Base58Codec.encode(mixed)))
    }

    @Test
    fun `characters the alphabet leaves out are refused rather than misread`() {
        // Base58 drops the four that look like each other; reading them as anything would decode an
        // address the user never had.
        listOf("0", "O", "I", "l", "9ceRgz579BcfWogs3RE11FKNQaWW7Lmtnev3MXspxUjF!").forEach {
            assertNull(Base58Codec.decode(it), it)
        }
    }

    @Test
    fun `the empty string decodes to no bytes rather than to a zero`() {
        assertArrayEquals(ByteArray(0), Base58Codec.decode(""))
        assertEquals("", Base58Codec.encode(ByteArray(0)))
    }
}

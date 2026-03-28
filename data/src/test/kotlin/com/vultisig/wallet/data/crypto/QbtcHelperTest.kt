package com.vultisig.wallet.data.crypto

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class QbtcHelperTest {

    @Test
    fun `deriveAddress returns valid bech32 address with qbtc prefix`() {
        val pubKeyHex = "a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2"
        val address = QbtcHelper.deriveAddress(pubKeyHex)

        assertTrue(address.startsWith("qbtc1"))
    }

    @Test
    fun `deriveAddress is deterministic`() {
        val pubKeyHex = "abcdef1234567890abcdef1234567890abcdef1234567890abcdef1234567890"
        val address1 = QbtcHelper.deriveAddress(pubKeyHex)
        val address2 = QbtcHelper.deriveAddress(pubKeyHex)

        assertEquals(address1, address2)
    }

    @Test
    fun `deriveAddress produces different addresses for different keys`() {
        val key1 = "a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2"
        val key2 = "b1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2"

        assertNotEquals(QbtcHelper.deriveAddress(key1), QbtcHelper.deriveAddress(key2))
    }

    @Test
    fun `deriveAddress contains only valid bech32 characters`() {
        val pubKeyHex = "deadbeef" + "00".repeat(28)
        val address = QbtcHelper.deriveAddress(pubKeyHex)

        val validChars = "qpzry9x8gf2tvdw0s3jn54khce6mua7l"
        val dataPart = address.substringAfter("qbtc1")
        for (c in dataPart) {
            assertTrue(c in validChars, "Character '$c' should be valid bech32")
        }
    }

    @Test
    fun `deriveAddress handles large MLDSA public key`() {
        // ML-DSA-44 public key is 1312 bytes
        val largePubKeyHex = "ab".repeat(1312)
        val address = QbtcHelper.deriveAddress(largePubKeyHex)

        assertTrue(address.startsWith("qbtc1"))
        assertTrue(address.length in 40..70)
    }

    @Test
    fun `deriveAddress produces consistent length output`() {
        val key32 = "ab".repeat(32)
        val key64 = "cd".repeat(64)
        val key1312 = "ef".repeat(1312)

        val addr32 = QbtcHelper.deriveAddress(key32)
        val addr64 = QbtcHelper.deriveAddress(key64)
        val addr1312 = QbtcHelper.deriveAddress(key1312)

        // RIPEMD160 always produces 20 bytes -> same bech32 length
        assertEquals(addr32.length, addr64.length)
        assertEquals(addr64.length, addr1312.length)
    }

    @Test
    fun `deriveAddress throws for invalid hex`() {
        assertThrows<IllegalArgumentException> { QbtcHelper.deriveAddress("not-valid-hex") }
    }

    @Test
    fun `deriveAddress handles empty key without crashing`() {
        // Empty hex produces empty byte array; SHA256+RIPEMD160 still work
        val address = QbtcHelper.deriveAddress("")
        assertTrue(address.startsWith("qbtc1"))
    }
}

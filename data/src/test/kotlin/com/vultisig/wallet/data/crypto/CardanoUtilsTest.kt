package com.vultisig.wallet.data.crypto

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * Tests for [CardanoUtils.createEnterpriseAddress] — guards against non-32-byte spending key inputs
 * that would otherwise surface as an [IllegalStateException] crash instead of a well-typed
 * [IllegalArgumentException] caught by the chain-enable error handler.
 *
 * Mirrors [com.vultisig.wallet.data.repositories.ChainAccountAddressRepositoryEddsaValidationTest]
 * for the Cardano-specific code path.
 */
class CardanoUtilsTest {

    @Test
    fun `createEnterpriseAddress throws for empty hex`() {
        val ex = assertThrows<IllegalArgumentException> { CardanoUtils.createEnterpriseAddress("") }
        assertEquals("spending key must be 32 bytes, got 0", ex.message)
    }

    @Test
    fun `createEnterpriseAddress throws for 31-byte hex (62 chars)`() {
        val ex =
            assertThrows<IllegalArgumentException> {
                CardanoUtils.createEnterpriseAddress("aa".repeat(31))
            }
        assertEquals("spending key must be 32 bytes, got 31", ex.message)
    }

    @Test
    fun `createEnterpriseAddress throws for 33-byte hex (66 chars)`() {
        val ex =
            assertThrows<IllegalArgumentException> {
                CardanoUtils.createEnterpriseAddress("aa".repeat(33))
            }
        assertEquals("spending key must be 32 bytes, got 33", ex.message)
    }

    @Test
    fun `createEnterpriseAddress throws for 1-byte hex`() {
        val ex =
            assertThrows<IllegalArgumentException> { CardanoUtils.createEnterpriseAddress("aa") }
        assertEquals("spending key must be 32 bytes, got 1", ex.message)
    }

    @Test
    fun `createExtendedKey throws for spending key shorter than 32 bytes`() {
        val ex =
            assertThrows<IllegalArgumentException> {
                CardanoUtils.createExtendedKey("aa".repeat(31), "bb".repeat(32))
            }
        assertEquals("spending key must be 32 bytes, got 31", ex.message)
    }

    @Test
    fun `createExtendedKey throws for chain code shorter than 32 bytes`() {
        val ex =
            assertThrows<IllegalArgumentException> {
                CardanoUtils.createExtendedKey("aa".repeat(32), "bb".repeat(31))
            }
        assertEquals("chain code must be 32 bytes, got 31", ex.message)
    }

    @Test
    fun `createExtendedKey throws for spending key longer than 32 bytes`() {
        val ex =
            assertThrows<IllegalArgumentException> {
                CardanoUtils.createExtendedKey("aa".repeat(33), "bb".repeat(32))
            }
        assertEquals("spending key must be 32 bytes, got 33", ex.message)
    }

    @Test
    fun `createExtendedKey throws for chain code longer than 32 bytes`() {
        val ex =
            assertThrows<IllegalArgumentException> {
                CardanoUtils.createExtendedKey("aa".repeat(32), "bb".repeat(33))
            }
        assertEquals("chain code must be 32 bytes, got 33", ex.message)
    }

    // Real signed envelope from a native ADA send that a Conway-era node rejected: a 3-element
    // array [body, witness_set, auxiliary_data(=f6 null)] that must become a 4-element array with
    // is_valid=true (f5) spliced before the trailing f6.
    private val legacyEnvelopeHex =
        "83a4008182582052e86a3e604ef6600f45d1cdb434eacbbaeffc763b02dd6d83bdbd8c825a01d2" +
            "01018282581d61df45a39eb0282d2f6d0c1e46ea30452ba18eb86723739cdfbede9cbb1a001e8480" +
            "82581d6150574c50e2c665f998457296a5d7ea1d789949d5e4e25adeee1a18251a026723d2021a00" +
            "028900031a0b736d01a1008182582075be85178816db3bc71a4f3e64e5c89866d8b7daae827ba9cf" +
            "4ecd1ed9e645d55840f802b0a258b5af9c78dd5cc062bfb8d7984a432af1808a0dd7282051613ed1" +
            "fc62d7e1953443177354137b29d6bf34bd6cf10c7eee442d1a448e3b497f12b00bf6"

    private fun hex(s: String) = s.chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    @Test
    fun `addIsValidFlag upgrades legacy 3-element envelope, body hash unchanged`() {
        val legacy = hex(legacyEnvelopeHex)
        val upgraded = CardanoUtils.addIsValidFlag(legacy)

        assertEquals(legacy.size + 1, upgraded.size)
        assertEquals(0x84.toByte(), upgraded[0]) // array(4)
        assertEquals(0xF5.toByte(), upgraded[upgraded.size - 2]) // is_valid = true
        assertEquals(0xF6.toByte(), upgraded[upgraded.size - 1]) // auxiliary_data = null
        // tx_id (blake2b of body element 0) must be identical before and after the splice.
        assertEquals(
            CardanoUtils.calculateCardanoTransactionHash(legacy),
            CardanoUtils.calculateCardanoTransactionHash(upgraded),
        )
    }

    @Test
    fun `addIsValidFlag leaves an already-4-element envelope unchanged`() {
        val upgraded = CardanoUtils.addIsValidFlag(hex(legacyEnvelopeHex))
        assertEquals(upgraded.toList(), CardanoUtils.addIsValidFlag(upgraded).toList())
    }
}

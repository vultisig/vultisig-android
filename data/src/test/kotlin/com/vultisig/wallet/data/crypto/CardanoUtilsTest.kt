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
        assertThrows<IllegalArgumentException> { CardanoUtils.createEnterpriseAddress("aa") }
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
        assertThrows<IllegalArgumentException> {
            CardanoUtils.createExtendedKey("aa".repeat(33), "bb".repeat(32))
        }
    }
}

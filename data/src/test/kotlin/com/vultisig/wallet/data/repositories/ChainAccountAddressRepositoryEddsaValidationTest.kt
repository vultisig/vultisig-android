package com.vultisig.wallet.data.repositories

import com.vultisig.wallet.data.models.Chain
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * Tests for [validateEddsaPubKey] — guards the EdDSA branch of [ChainAccountAddressRepositoryImpl]
 * from passing malformed input into Trust Wallet Core's `PublicKey` JNI constructor, which would
 * otherwise throw a generic `java.security.InvalidParameterException` and crash the chain selection
 * commit batch.
 */
class ChainAccountAddressRepositoryEddsaValidationTest {

    private val validKey = "a".repeat(64)

    @Test
    fun `valid 64-char lowercase hex key passes validation`() {
        assertDoesNotThrow { validateEddsaPubKey(Chain.Solana, validKey) }
    }

    @Test
    fun `valid 64-char uppercase hex key passes validation`() {
        assertDoesNotThrow { validateEddsaPubKey(Chain.Solana, "A".repeat(64)) }
    }

    @Test
    fun `valid mixed-case hex key passes validation`() {
        assertDoesNotThrow {
            validateEddsaPubKey(
                Chain.Solana,
                "DeadBeefCafe1234567890abcdefABCDEF0123456789abcdef0123456789abcd",
            )
        }
    }

    @Test
    fun `blank key throws IllegalArgumentException`() {
        val ex = assertThrows<IllegalArgumentException> { validateEddsaPubKey(Chain.Solana, "") }
        assertEquals("EdDSA public key for ${Chain.Solana.raw} is missing", ex.message)
    }

    @Test
    fun `whitespace-only key throws IllegalArgumentException`() {
        assertThrows<IllegalArgumentException> { validateEddsaPubKey(Chain.Solana, "   ") }
    }

    @Test
    fun `key shorter than 64 chars throws IllegalArgumentException`() {
        val ex =
            assertThrows<IllegalArgumentException> {
                validateEddsaPubKey(Chain.Solana, "a".repeat(62))
            }
        assertEquals(
            "EdDSA public key for ${Chain.Solana.raw} has invalid length: 62 " +
                "(expected 64 hex characters)",
            ex.message,
        )
    }

    @Test
    fun `key longer than 64 chars throws IllegalArgumentException`() {
        assertThrows<IllegalArgumentException> { validateEddsaPubKey(Chain.Solana, "a".repeat(66)) }
    }

    @Test
    fun `odd-length key throws IllegalArgumentException`() {
        assertThrows<IllegalArgumentException> { validateEddsaPubKey(Chain.Solana, "a".repeat(63)) }
    }

    @Test
    fun `non-hex characters throw IllegalArgumentException`() {
        val ex =
            assertThrows<IllegalArgumentException> {
                validateEddsaPubKey(Chain.Solana, "g".repeat(64))
            }
        assertEquals(
            "EdDSA public key for ${Chain.Solana.raw} contains non-hex characters",
            ex.message,
        )
    }

    @Test
    fun `64 chars with whitespace throws IllegalArgumentException`() {
        val keyWithSpace = "a".repeat(63) + " "
        assertThrows<IllegalArgumentException> { validateEddsaPubKey(Chain.Solana, keyWithSpace) }
    }
}

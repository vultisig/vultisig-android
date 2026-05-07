package com.vultisig.wallet.data.repositories

import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Vault
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

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

    /**
     * Covers every EdDSA chain so a future helper that runs ahead of [validateEddsaPubKey] (e.g. a
     * Cardano- or Bittensor-specific branch) can't silently bypass the guard for that chain.
     */
    @ParameterizedTest
    @EnumSource(EddsaChain::class)
    fun `blank key throws for every EdDSA chain`(chain: EddsaChain) {
        val ex = assertThrows<IllegalArgumentException> { validateEddsaPubKey(chain.value, "") }
        assertEquals("EdDSA public key for ${chain.value.raw} is missing", ex.message)
    }

    @ParameterizedTest
    @EnumSource(EddsaChain::class)
    fun `non-hex key throws for every EdDSA chain`(chain: EddsaChain) {
        assertThrows<IllegalArgumentException> { validateEddsaPubKey(chain.value, "z".repeat(64)) }
    }

    /**
     * End-to-end coverage that [ChainAccountAddressRepositoryImpl.getAddress] runs the validator
     * ahead of any consumer of `eddsaPubKey`. A future edit that re-orders the validate call below
     * `eddsaPubKey.hexToByteArray()` or below the Cardano/Bittensor branches would fail this test
     * with a non-`IllegalArgumentException` (e.g. JNI `InvalidParameterException`).
     */
    @Test
    fun `getAddress rejects blank pubKeyEDDSA before reaching JNI`() = runBlocking {
        val repo = ChainAccountAddressRepositoryImpl()
        val vault = Vault(id = "test-vault", name = "test", pubKeyECDSA = "", pubKeyEDDSA = "")
        val ex = assertThrows<IllegalArgumentException> { repo.getAddress(Chain.Solana, vault) }
        assertEquals("EdDSA public key for ${Chain.Solana.raw} is missing", ex.message)
    }

    /** The 6 EdDSA chains in this codebase — kept in sync with `Chain.TssKeysignType`. */
    enum class EddsaChain(val value: Chain) {
        SOLANA(Chain.Solana),
        POLKADOT(Chain.Polkadot),
        BITTENSOR(Chain.Bittensor),
        SUI(Chain.Sui),
        TON(Chain.Ton),
        CARDANO(Chain.Cardano),
    }
}

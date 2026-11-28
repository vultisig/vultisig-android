package com.vultisig.wallet.data.models

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class VaultHasValidMldsaKeyTest {

    private val vault = Vault(id = "id", name = "name")

    @Test
    fun `blank pubKeyMLDSA returns false`() {
        assertFalse(vault.hasValidMldsaKey())
        assertFalse(
            vault
                .copy(keyshares = listOf(KeyShare(pubKey = MLDSA65_KEY, keyShare = "share")))
                .hasValidMldsaKey()
        )
    }

    @Test
    fun `pubKeyMLDSA without matching keyshare returns false`() {
        val noShares = vault.copy(pubKeyMLDSA = MLDSA65_KEY)
        assertFalse(noShares.hasValidMldsaKey())

        val otherSharesOnly =
            vault.copy(
                pubKeyMLDSA = MLDSA65_KEY,
                keyshares =
                    listOf(
                        KeyShare(pubKey = "ecdsaKey", keyShare = "share1"),
                        KeyShare(pubKey = "eddsaKey", keyShare = "share2"),
                    ),
            )
        assertFalse(otherSharesOnly.hasValidMldsaKey())
    }

    @Test
    fun `ML-DSA-65 pubKeyMLDSA with matching keyshare returns true`() {
        val valid =
            vault.copy(
                pubKeyMLDSA = MLDSA65_KEY,
                keyshares =
                    listOf(
                        KeyShare(pubKey = "ecdsaKey", keyShare = "share1"),
                        KeyShare(pubKey = MLDSA65_KEY, keyShare = "share2"),
                    ),
            )
        assertTrue(valid.hasValidMldsaKey())
    }

    @Test
    fun `legacy ML-DSA-44 pubKeyMLDSA with matching keyshare returns false`() {
        val legacy =
            vault.copy(
                pubKeyMLDSA = MLDSA44_KEY,
                keyshares = listOf(KeyShare(pubKey = MLDSA44_KEY, keyShare = "share")),
            )
        assertFalse(legacy.hasValidMldsaKey())
    }

    @Test
    fun `isMldsa65PublicKey accepts only 1952 bytes of hex`() {
        assertTrue(isMldsa65PublicKey(MLDSA65_KEY))
        assertTrue(isMldsa65PublicKey(MLDSA65_KEY.uppercase()))
        assertFalse(isMldsa65PublicKey(""))
        assertFalse(isMldsa65PublicKey(MLDSA44_KEY))
        assertFalse(isMldsa65PublicKey(MLDSA65_KEY + "ab"))
        assertFalse(isMldsa65PublicKey("zz".repeat(MLDSA65_PUBLIC_KEY_BYTES)))
    }

    @Test
    fun `legacyMldsa44PublicKey reads a legacy pubKeyMLDSA`() {
        assertEquals(MLDSA44_KEY, vault.copy(pubKeyMLDSA = MLDSA44_KEY).legacyMldsa44PublicKey())
    }

    @Test
    fun `legacyMldsa44PublicKey finds the legacy keyshare after an ML-DSA-65 keygen`() {
        val upgraded =
            vault.copy(
                pubKeyMLDSA = MLDSA65_KEY,
                keyshares =
                    listOf(
                        KeyShare(pubKey = "ecdsaKey", keyShare = "share1"),
                        KeyShare(pubKey = MLDSA44_KEY, keyShare = "share2"),
                        KeyShare(pubKey = MLDSA65_KEY, keyShare = "share3"),
                    ),
            )
        assertEquals(MLDSA44_KEY, upgraded.legacyMldsa44PublicKey())
    }

    @Test
    fun `legacyMldsa44PublicKey is empty for a vault that never had one`() {
        val fresh =
            vault.copy(
                pubKeyMLDSA = MLDSA65_KEY,
                keyshares = listOf(KeyShare(pubKey = MLDSA65_KEY, keyShare = "share")),
            )
        assertEquals("", fresh.legacyMldsa44PublicKey())
        assertEquals("", vault.legacyMldsa44PublicKey())
    }

    private companion object {
        val MLDSA65_KEY = "ab".repeat(MLDSA65_PUBLIC_KEY_BYTES)
        val MLDSA44_KEY = "cd".repeat(MLDSA44_PUBLIC_KEY_BYTES)
    }
}

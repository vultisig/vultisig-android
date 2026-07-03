package com.vultisig.wallet.data.models

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
                .copy(keyshares = listOf(KeyShare(pubKey = "mldsaKey", keyShare = "share")))
                .hasValidMldsaKey()
        )
    }

    @Test
    fun `pubKeyMLDSA without matching keyshare returns false`() {
        val noShares = vault.copy(pubKeyMLDSA = "mldsaKey")
        assertFalse(noShares.hasValidMldsaKey())

        val otherSharesOnly =
            vault.copy(
                pubKeyMLDSA = "mldsaKey",
                keyshares =
                    listOf(
                        KeyShare(pubKey = "ecdsaKey", keyShare = "share1"),
                        KeyShare(pubKey = "eddsaKey", keyShare = "share2"),
                    ),
            )
        assertFalse(otherSharesOnly.hasValidMldsaKey())
    }

    @Test
    fun `pubKeyMLDSA with matching keyshare returns true`() {
        val valid =
            vault.copy(
                pubKeyMLDSA = "mldsaKey",
                keyshares =
                    listOf(
                        KeyShare(pubKey = "ecdsaKey", keyShare = "share1"),
                        KeyShare(pubKey = "mldsaKey", keyShare = "share2"),
                    ),
            )
        assertTrue(valid.hasValidMldsaKey())
    }
}

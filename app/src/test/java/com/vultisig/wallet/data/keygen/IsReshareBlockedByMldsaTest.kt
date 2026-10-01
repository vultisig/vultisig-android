package com.vultisig.wallet.data.keygen

import com.vultisig.wallet.data.models.SigningLibType
import com.vultisig.wallet.data.models.TssAction
import com.vultisig.wallet.data.models.Vault
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class IsReshareBlockedByMldsaTest {

    private val mldsaVault =
        Vault(
            id = "vault",
            name = "Vault",
            pubKeyECDSA = "ecdsa",
            pubKeyMLDSA = "mldsa-pk",
            libType = SigningLibType.DKLS,
        )

    private val plainVault = mldsaVault.copy(pubKeyMLDSA = "")

    @Test
    fun `reshare of a vault with an MLDSA key is blocked`() {
        assertTrue(isReshareBlockedByMldsa(TssAction.ReShare, mldsaVault))
    }

    @Test
    fun `reshare is blocked even when the MLDSA keyshare is not readable`() {
        assertTrue(
            isReshareBlockedByMldsa(TssAction.ReShare, mldsaVault.copy(keyshares = emptyList()))
        )
    }

    @Test
    fun `reshare of a vault without an MLDSA key is allowed`() {
        assertFalse(isReshareBlockedByMldsa(TssAction.ReShare, plainVault))
    }

    @Test
    fun `new device joining a reshare is not blocked`() {
        assertFalse(isReshareBlockedByMldsa(TssAction.ReShare, existingVault = null))
    }

    @Test
    fun `migrate is not blocked by an MLDSA key`() {
        assertFalse(isReshareBlockedByMldsa(TssAction.Migrate, mldsaVault))
    }
}

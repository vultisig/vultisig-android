package com.vultisig.wallet.data.models

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class VaultHasPreGeneratedKeyTest {

    private val vault = Vault(id = "id", name = "name")

    @Test
    fun `non-KeyImport vault always returns true`() {
        val gg20Vault = vault.copy(libType = SigningLibType.GG20)
        assertTrue(gg20Vault.hasPreGeneratedKey(Chain.Ethereum))
        assertTrue(gg20Vault.hasPreGeneratedKey(Chain.Solana))

        val dklsVault = vault.copy(libType = SigningLibType.DKLS)
        assertTrue(dklsVault.hasPreGeneratedKey(Chain.Bitcoin))
    }

    @Test
    fun `exact ECDSA chain match returns true`() {
        val keyImportVault =
            vault.copy(
                libType = SigningLibType.KeyImport,
                chainPublicKeys =
                    listOf(
                        ChainPublicKey(chain = "Ethereum", publicKey = "ethKey", isEddsa = false)
                    ),
            )
        assertTrue(keyImportVault.hasPreGeneratedKey(Chain.Ethereum))
    }

    @Test
    fun `exact EdDSA chain match returns true`() {
        val keyImportVault =
            vault.copy(
                libType = SigningLibType.KeyImport,
                chainPublicKeys =
                    listOf(ChainPublicKey(chain = "Solana", publicKey = "solKey", isEddsa = true)),
            )
        assertTrue(keyImportVault.hasPreGeneratedKey(Chain.Solana))
    }

    @Test
    fun `ECDSA key does not satisfy EdDSA chain`() {
        val keyImportVault =
            vault.copy(
                libType = SigningLibType.KeyImport,
                chainPublicKeys =
                    listOf(ChainPublicKey(chain = "Solana", publicKey = "solKey", isEddsa = false)),
            )
        assertFalse(keyImportVault.hasPreGeneratedKey(Chain.Solana))
    }

    @Test
    fun `EdDSA key does not satisfy ECDSA chain`() {
        val keyImportVault =
            vault.copy(
                libType = SigningLibType.KeyImport,
                chainPublicKeys =
                    listOf(ChainPublicKey(chain = "Ethereum", publicKey = "ethKey", isEddsa = true)),
            )
        assertFalse(keyImportVault.hasPreGeneratedKey(Chain.Ethereum))
    }

    @Test
    fun `blank publicKey returns false for KeyImport`() {
        val keyImportVault =
            vault.copy(
                libType = SigningLibType.KeyImport,
                chainPublicKeys =
                    listOf(
                        ChainPublicKey(chain = "Ethereum", publicKey = "", isEddsa = false),
                        ChainPublicKey(chain = "Solana", publicKey = "   ", isEddsa = true),
                    ),
            )
        assertFalse(keyImportVault.hasPreGeneratedKey(Chain.Ethereum))
        assertFalse(keyImportVault.hasPreGeneratedKey(Chain.Solana))
    }

    @Test
    fun `empty chainPublicKeys returns false for KeyImport`() {
        val keyImportVault =
            vault.copy(libType = SigningLibType.KeyImport, chainPublicKeys = emptyList())
        assertFalse(keyImportVault.hasPreGeneratedKey(Chain.Ethereum))
        assertFalse(keyImportVault.hasPreGeneratedKey(Chain.Solana))
    }
}

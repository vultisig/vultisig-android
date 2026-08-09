package com.vultisig.wallet.data.models

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class VaultResolveEcdsaChainTest {

    private val vault = Vault(id = "id", name = "name")

    @Test
    fun `non-KeyImport vault always returns the preferred chain`() {
        val gg20Vault = vault.copy(libType = SigningLibType.GG20)
        assertEquals(Chain.Ethereum, gg20Vault.resolveEcdsaChain(Chain.Ethereum))
    }

    @Test
    fun `KeyImport vault with an exact match returns the preferred chain`() {
        val keyImportVault =
            vault.copy(
                libType = SigningLibType.KeyImport,
                chainPublicKeys =
                    listOf(
                        ChainPublicKey(chain = "Ethereum", publicKey = "ethKey", isEddsa = false)
                    ),
            )
        assertEquals(Chain.Ethereum, keyImportVault.resolveEcdsaChain(Chain.Ethereum))
    }

    // The derivation-path-sibling fallback (e.g. BSC standing in for Ethereum) relies on
    // Chain.coinType.compatibleDerivationPath(), which calls into the native Trust Wallet Core
    // library and can't be exercised in a JVM unit test — same constraint as the existing
    // getEcdsaSigningKey/getEddsaSigningKey/getPubKeyByChain fallbacks in this file.
}

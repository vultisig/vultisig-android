package com.vultisig.wallet.data.models

import com.vultisig.wallet.data.models.LOCAL_PARTY_ID_PREFIX
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import wallet.core.jni.CoinType

class QbtcChainConfigTest {

    @Test
    fun `QBTC chain uses Cosmos standard`() {
        assertEquals(TokenStandard.COSMOS, Chain.Qbtc.standard)
    }

    @Test
    fun `QBTC chain uses MLDSA key type`() {
        assertEquals(TssKeyType.MLDSA, Chain.Qbtc.TssKeysignType)
    }

    @Test
    fun `QBTC chain uses Cosmos coin type`() {
        assertEquals(CoinType.COSMOS, Chain.Qbtc.coinType)
    }

    @Test
    fun `QBTC chain ticker is QBTC`() {
        assertEquals("QBTC", Chain.Qbtc.ticker())
    }

    @Test
    fun `QBTC chain feeUnit is qbtc`() {
        assertEquals("qbtc", Chain.Qbtc.feeUnit)
    }

    @Test
    fun `QBTC is not swap supported`() {
        assertFalse(Chain.Qbtc.isSwapSupported)
    }

    @Test
    fun `QBTC is not deposit supported`() {
        assertFalse(Chain.Qbtc.isDepositSupported)
    }

    @Test
    fun `MLDSA is distinct from ECDSA and EDDSA`() {
        assertTrue(TssKeyType.MLDSA != TssKeyType.ECDSA)
        assertTrue(TssKeyType.MLDSA != TssKeyType.EDDSA)
    }

    @Test
    fun `only QBTC uses MLDSA key type`() {
        val mldsaChains = Chain.entries.filter { it.TssKeysignType == TssKeyType.MLDSA }
        assertEquals(listOf(Chain.Qbtc), mldsaChains)
    }

    @Test
    fun `vault getPubKeyByChain returns MLDSA key for QBTC`() {
        val vault =
            Vault(
                id = "test-vault",
                name = "Test",
                pubKeyECDSA = "ecdsa_key",
                pubKeyEDDSA = "eddsa_key",
                pubKeyMLDSA = "mldsa_key_hex",
            )
        assertEquals("mldsa_key_hex", vault.getPubKeyByChain(Chain.Qbtc))
    }

    @Test
    fun `vault getPubKeyByChain returns ECDSA for Bitcoin`() {
        val vault =
            Vault(
                id = "test-vault",
                name = "Test",
                pubKeyECDSA = "ecdsa_key",
                pubKeyEDDSA = "eddsa_key",
                pubKeyMLDSA = "mldsa_key_hex",
            )
        assertEquals("ecdsa_key", vault.getPubKeyByChain(Chain.Bitcoin))
    }

    @Test
    fun `vault getPubKeyByChain returns EDDSA for Solana`() {
        val vault =
            Vault(
                id = "test-vault",
                name = "Test",
                pubKeyECDSA = "ecdsa_key",
                pubKeyEDDSA = "eddsa_key",
                pubKeyMLDSA = "mldsa_key_hex",
            )
        assertEquals("eddsa_key", vault.getPubKeyByChain(Chain.Solana))
    }

    @Test
    fun `QBTC works with secure vault (co-sign)`() {
        val vault =
            Vault(
                id = "secure-vault",
                name = "Secure",
                pubKeyMLDSA = "mldsa_key",
                signers = listOf("device1", "device2", "device3"),
                localPartyID = "device1",
            )
        assertFalse(vault.isFastVault(), "Secure vault (co-sign) should not be fast vault")
        assertEquals("mldsa_key", vault.getPubKeyByChain(Chain.Qbtc))
    }

    @Test
    fun `QBTC works with fast vault`() {
        val vault =
            Vault(
                id = "fast-vault",
                name = "FastVault",
                pubKeyMLDSA = "mldsa_key",
                signers = listOf("device1", "device2", LOCAL_PARTY_ID_PREFIX + "-server"),
                localPartyID = "device1",
            )
        assertTrue(vault.isFastVault(), "Should be detected as fast vault")
        assertEquals("mldsa_key", vault.getPubKeyByChain(Chain.Qbtc))
    }

    @Test
    fun `QBTC is excluded from key import supported chains`() {
        assertFalse(
            Chain.Qbtc in Chain.keyImportSupportedChains,
            "QBTC should not be in key import chains",
        )
    }

    @Test
    fun `QBTC coin has correct decimals`() {
        val qbtcCoin = Coins.Qbtc.QBTC
        assertEquals(8, qbtcCoin.decimal)
    }

    @Test
    fun `QBTC coin is native token`() {
        assertTrue(Coins.Qbtc.QBTC.isNativeToken)
    }

    @Test
    fun `QBTC coin ticker matches chain`() {
        assertEquals("QBTC", Coins.Qbtc.QBTC.ticker)
    }

    @Test
    fun `QBTC coin is in coins map`() {
        val coins = Coins.coins
        assertTrue(coins.containsKey(Chain.Qbtc), "Coins map should contain QBTC")
        assertEquals(1, coins[Chain.Qbtc]?.size)
    }

    @Test
    fun `QBTC coin has empty price provider`() {
        assertEquals("", Coins.Qbtc.QBTC.priceProviderID)
    }
}

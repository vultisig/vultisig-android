package com.vultisig.wallet.data.models

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CoinTest {

    private fun coin(chain: Chain, isNativeToken: Boolean) =
        Coin(
            chain = chain,
            ticker = "",
            logo = "",
            address = "",
            decimal = 0,
            hexPublicKey = "",
            priceProviderID = "",
            contractAddress = "",
            isNativeToken = isNativeToken,
        )

    @Test
    fun `native Polkadot does not carry memo`() {
        assertFalse(coin(Chain.Polkadot, isNativeToken = true).carriesMemo)
    }

    @Test
    fun `native Bittensor does not carry memo`() {
        assertFalse(coin(Chain.Bittensor, isNativeToken = true).carriesMemo)
    }

    @Test
    fun `native Sui does not carry memo`() {
        assertFalse(coin(Chain.Sui, isNativeToken = true).carriesMemo)
    }

    @Test
    fun `native Cosmos family carries memo`() {
        assertTrue(coin(Chain.GaiaChain, isNativeToken = true).carriesMemo)
    }

    @Test
    fun `native Ton carries memo`() {
        assertTrue(coin(Chain.Ton, isNativeToken = true).carriesMemo)
    }

    @Test
    fun `native Solana carries memo`() {
        assertTrue(coin(Chain.Solana, isNativeToken = true).carriesMemo)
    }

    @Test
    fun `native Tron carries memo`() {
        assertTrue(coin(Chain.Tron, isNativeToken = true).carriesMemo)
    }

    @Test
    fun `other native chain carries memo`() {
        assertTrue(coin(Chain.Ethereum, isNativeToken = true).carriesMemo)
    }
}

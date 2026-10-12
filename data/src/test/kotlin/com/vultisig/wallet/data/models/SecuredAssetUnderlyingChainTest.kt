package com.vultisig.wallet.data.models

import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.jupiter.api.Test

internal class SecuredAssetUnderlyingChainTest {

    @Test
    fun `resolves the chain a secured asset is pegged to`() {
        assertEquals(
            Chain.Ethereum,
            securedAsset("USDC", "eth-usdc-0xa0b86991c6218b36c1d19d4a2e9eb0ce3606eb48")
                .securedAssetUnderlyingChain(),
        )
        assertEquals(
            Chain.BscChain,
            securedAsset("USDC", "bsc-usdc-0x8ac76a51cc950d9822d68b83fe1ad97b32cd580d")
                .securedAssetUnderlyingChain(),
        )
        assertEquals(
            Chain.Avalanche,
            securedAsset("USDC", "avax-usdc-0xb97ef9ef8734c71904d8002f8b6bc66dd9c48a6e")
                .securedAssetUnderlyingChain(),
        )
        assertEquals(Chain.Base, securedAsset("ETH", "base-eth").securedAssetUnderlyingChain())
        assertEquals(
            Chain.BitcoinCash,
            securedAsset("BCH", "bch-bch").securedAssetUnderlyingChain(),
        )
    }

    @Test
    fun `is null for coins that are not secured assets`() {
        assertNull(Coins.ThorChain.RUNE.securedAssetUnderlyingChain())
        assertNull(Coins.ThorChain.TCY.securedAssetUnderlyingChain())
        assertNull(Coins.Ethereum.USDC.securedAssetUnderlyingChain())
    }

    @Test
    fun `is null for a chain code Vultisig does not support`() {
        assertNull(securedAsset("FOO", "unknownchain-foo").securedAssetUnderlyingChain())
    }

    private fun securedAsset(ticker: String, contractAddress: String) =
        Coins.ThorChain.RUNE.copy(
            ticker = ticker,
            contractAddress = contractAddress,
            isNativeToken = false,
        )
}

package com.vultisig.wallet.data.models

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test

internal class CoinCatalogKeyTest {

    private fun coin(
        chain: Chain,
        ticker: String,
        contractAddress: String,
        isNativeToken: Boolean = false,
    ) =
        Coin(
            chain = chain,
            ticker = ticker,
            logo = "",
            address = "",
            decimal = 0,
            hexPublicKey = "",
            priceProviderID = "",
            contractAddress = contractAddress,
            isNativeToken = isNativeToken,
        )

    @Test
    fun `secured assets sharing a ticker on different underlying chains have distinct catalog keys`() {
        val ethUsdc =
            coin(Chain.ThorChain, "USDC", "eth-usdc-0xa0b86991c6218b36c1d19d4a2e9eb0ce3606eb48")
        val avaxUsdc =
            coin(Chain.ThorChain, "USDC", "avax-usdc-0xb97ef9ef8734c71904d8002f8b6bc66dd9c48a6e")

        assertEquals(ethUsdc.id, avaxUsdc.id) // both collapse to "USDC-thorchain"
        assertNotEquals(ethUsdc.catalogKey, avaxUsdc.catalogKey)
    }

    @Test
    fun `a regular (non-secured) coin's catalog key equals its id`() {
        val c = coin(Chain.Ethereum, "USDC", "0xa0b86991c6218b36c1d19d4a2e9eb0ce3606eb48")
        assertEquals(c.id, c.catalogKey)
    }

    @Test
    fun `native coins use id as the catalog key`() {
        val c = coin(Chain.ThorChain, "RUNE", "", isNativeToken = true)
        assertEquals(c.id, c.catalogKey)
    }
}

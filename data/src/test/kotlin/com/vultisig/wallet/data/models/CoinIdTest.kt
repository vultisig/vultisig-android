package com.vultisig.wallet.data.models

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test

internal class CoinIdTest {

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
    fun `secured assets sharing a ticker on different underlying chains have distinct ids`() {
        val ethUsdc =
            coin(Chain.ThorChain, "USDC", "eth-usdc-0xa0b86991c6218b36c1d19d4a2e9eb0ce3606eb48")
        val avaxUsdc =
            coin(Chain.ThorChain, "USDC", "avax-usdc-0xb97ef9ef8734c71904d8002f8b6bc66dd9c48a6e")

        assertNotEquals(ethUsdc.id, avaxUsdc.id)
    }

    @Test
    fun `the same secured asset always resolves to the same id`() {
        val a = coin(Chain.ThorChain, "USDC", "eth-usdc-0xa0b86991c6218b36c1d19d4a2e9eb0ce3606eb48")
        val b = coin(Chain.ThorChain, "USDC", "eth-usdc-0xa0b86991c6218b36c1d19d4a2e9eb0ce3606eb48")

        assertEquals(a.id, b.id)
    }

    @Test
    fun `a regular (non-secured) coin's id is unaffected — ticker-chainId, no contract`() {
        val c = coin(Chain.Ethereum, "USDC", "0xa0b86991c6218b36c1d19d4a2e9eb0ce3606eb48")
        assertEquals("USDC-Ethereum", c.id)
    }

    @Test
    fun `native coins keep the plain ticker-chainId id`() {
        val c = coin(Chain.ThorChain, "RUNE", "", isNativeToken = true)
        assertEquals("RUNE-THORChain", c.id)
    }

    @Test
    fun `same ticker TON jettons with different contracts get distinct ids`() {
        val first = coin(Chain.Ton, "USDJ", "EQFirstJetton")
        val second = coin(Chain.Ton, "USDJ", "EQSecondJetton")

        assertNotEquals(first.id, second.id)
        assertEquals("USDJ-Ton-EQFirstJetton", first.id)
        assertEquals("USDJ-Ton-EQSecondJetton", second.id)
    }

    @Test
    fun `same ticker TRON tokens with different contracts get distinct ids`() {
        val first = coin(Chain.Tron, "USDX", "TFirstTrc20")
        val second = coin(Chain.Tron, "USDX", "TSecondTrc20")

        assertNotEquals(first.id, second.id)
        assertEquals("USDX-Tron-TFirstTrc20", first.id)
        assertEquals("USDX-Tron-TSecondTrc20", second.id)
    }

    @Test
    fun `native TON and TRON coins keep unqualified ids`() {
        assertEquals("TON-Ton", coin(Chain.Ton, "TON", "", isNativeToken = true).id)
        assertEquals("TRX-Tron", coin(Chain.Tron, "TRX", "", isNativeToken = true).id)
    }
}

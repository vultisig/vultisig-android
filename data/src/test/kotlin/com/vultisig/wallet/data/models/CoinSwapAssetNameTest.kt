package com.vultisig.wallet.data.models

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class CoinSwapAssetNameTest {

    private fun coin(
        chain: Chain,
        ticker: String,
        contractAddress: String,
        isNativeToken: Boolean,
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

    // swapAssetName — native tokens

    @Test
    fun `native RUNE on ThorChain returns THOR dot RUNE`() {
        val c = coin(Chain.ThorChain, "RUNE", "", isNativeToken = true)
        assertEquals("THOR.RUNE", c.swapAssetName())
    }

    @Test
    fun `native ETH on Ethereum returns ETH dot ETH`() {
        val c = coin(Chain.Ethereum, "ETH", "", isNativeToken = true)
        assertEquals("ETH.ETH", c.swapAssetName())
    }

    @Test
    fun `native ATOM on GaiaChain returns GAIA dot ATOM not GAIA dot ATOM ticker`() {
        val c = coin(Chain.GaiaChain, "ATOM", "", isNativeToken = true)
        assertEquals("GAIA.ATOM", c.swapAssetName())
    }

    // swapAssetName — Kujira non-native

    @Test
    fun `Kujira ibc token returns KUJI dot ticker`() {
        val c = coin(Chain.Kujira, "USDC", "ibc/HASH123ABC", isNativeToken = false)
        assertEquals("KUJI.USDC", c.swapAssetName())
    }

    @Test
    fun `Kujira factory token returns KUJI dot ticker`() {
        val c = coin(Chain.Kujira, "USK", "factory/kujira/usk", isNativeToken = false)
        assertEquals("KUJI.USK", c.swapAssetName())
    }

    @Test
    fun `Kujira token with plain contract address includes contract`() {
        val c = coin(Chain.Kujira, "FOO", "kujiraabcdef", isNativeToken = false)
        assertEquals("KUJI.FOO-kujiraabcdef", c.swapAssetName())
    }

    // swapAssetName — ThorChain non-native (secured assets)

    @Test
    fun `ThorChain secured asset returns the raw contractAddress verbatim`() {
        // Thornode's quote endpoint expects the dash-separated denom as-is (matching iOS's
        // Coin.swapAsset), not a dot-normalized "CHAIN.SYMBOL" form — see swapAssetComparisonName
        // for where the dot form is still needed, for same-asset identity comparisons only.
        listOf("btc-btc", "eth-eth", "bch-bch", "ltc-ltc", "doge-doge", "avax-avax", "bsc-bnb")
            .forEach { denom ->
                val c =
                    coin(Chain.ThorChain, denom.substringAfter("-"), denom, isNativeToken = false)
                assertEquals(denom, c.swapAssetName())
            }
    }

    @Test
    fun `ThorChain address without dash returns THOR dot ticker`() {
        val c = coin(Chain.ThorChain, "USDC", "sometoken", isNativeToken = false)
        assertEquals("THOR.USDC", c.swapAssetName())
    }

    @Test
    fun `ThorChain secured EVM token returns the raw contractAddress with hex tail case intact`() {
        val c =
            coin(
                Chain.ThorChain,
                "USDC",
                "eth-usdc-0xa0b86991c6218b36c1d19d4a2e9eb0ce3606eb48",
                isNativeToken = false,
            )
        assertEquals("eth-usdc-0xa0b86991c6218b36c1d19d4a2e9eb0ce3606eb48", c.swapAssetName())
    }

    @Test
    fun `ThorChain secured EVM token with EIP-55 checksum hex tail preserves case`() {
        val c =
            coin(
                Chain.ThorChain,
                "USDC",
                "eth-usdc-0xA0b86991c6218b36c1d19D4a2e9Eb0cE3606eB48",
                isNativeToken = false,
            )
        assertEquals("eth-usdc-0xA0b86991c6218b36c1d19D4a2e9Eb0cE3606eB48", c.swapAssetName())
    }

    @Test
    fun `ThorChain RUJI single-segment contract address returns THOR dot RUJI`() {
        val c = coin(Chain.ThorChain, "RUJI", "ruji", isNativeToken = false)
        assertEquals("THOR.RUJI", c.swapAssetName())
    }

    // swapAssetName — EVM non-native

    @Test
    fun `EVM token returns chain dot ticker dash contractAddress`() {
        val c =
            coin(
                Chain.Ethereum,
                "USDC",
                "0xA0b86991c6218b36c1d19D4a2e9Eb0cE3606eB48",
                isNativeToken = false,
            )
        assertEquals("ETH.USDC-0xA0b86991c6218b36c1d19D4a2e9Eb0cE3606eB48", c.swapAssetName())
    }

    // swapAssetComparisonName — EVM

    @Test
    fun `EVM swapAssetComparisonName lowercases the whole name`() {
        val c =
            coin(
                Chain.Ethereum,
                "USDC",
                "0xA0b86991c6218b36c1d19D4a2e9Eb0cE3606eB48",
                isNativeToken = false,
            )
        assertEquals(
            "eth.usdc-0xa0b86991c6218b36c1d19d4a2e9eb0ce3606eb48",
            c.swapAssetComparisonName(),
        )
    }

    @Test
    fun `two EVM tokens with different-case contract addresses compare equal`() {
        val upper =
            coin(
                Chain.Ethereum,
                "USDC",
                "0xA0b86991c6218b36c1d19D4a2e9Eb0cE3606eB48",
                isNativeToken = false,
            )
        val lower =
            coin(
                Chain.Ethereum,
                "USDC",
                "0xa0b86991c6218b36c1d19d4a2e9eb0ce3606eb48",
                isNativeToken = false,
            )
        assertEquals(upper.swapAssetComparisonName(), lower.swapAssetComparisonName())
    }

    // swapAssetComparisonName — non-EVM (unchanged)

    @Test
    fun `non-EVM swapAssetComparisonName equals swapAssetName`() {
        val c = coin(Chain.ThorChain, "RUNE", "", isNativeToken = true)
        assertEquals(c.swapAssetName(), c.swapAssetComparisonName())
    }

    @Test
    fun `ThorChain secured asset comparison name lowercased for cross-chain identity`() {
        val c = coin(Chain.ThorChain, "ETH", "eth-eth", isNativeToken = false)
        assertEquals("eth.eth", c.swapAssetComparisonName())
    }

    @Test
    fun `ThorChain secured EVM asset and its Ethereum counterpart compare equal`() {
        val thorSide =
            coin(
                Chain.ThorChain,
                "USDC",
                "eth-usdc-0xA0b86991c6218b36c1d19D4a2e9Eb0cE3606eB48",
                isNativeToken = false,
            )
        val ethSide =
            coin(
                Chain.Ethereum,
                "USDC",
                "0xa0b86991c6218b36c1d19d4a2e9eb0ce3606eb48",
                isNativeToken = false,
            )
        assertEquals(thorSide.swapAssetComparisonName(), ethSide.swapAssetComparisonName())
    }
}

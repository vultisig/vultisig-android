package com.vultisig.wallet.data.repositories.swap

import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.SwapProvider
import com.vultisig.wallet.data.models.isSwapSupported
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Pins the SWAPKIT slice of [SwapProviderTableImpl]'s eligibility matrix — the most fan-out-prone
 * part of the integration. A regression that drops SWAPKIT from an EVM/Solana branch, or that adds
 * it to [SwapProviderTableImpl.sameChainOnly] (which would silently kill cross-chain SwapKit
 * quoting), must fail CI rather than ship a quietly-degraded provider list.
 */
internal class SwapProviderTableTest {

    private val table = SwapProviderTableImpl()

    @Test
    fun `SwapKit is offered on every Phase 1 SwapKit chain`() {
        // Each (chain, ticker, native) pair is a chain SwapKit routes on in Phase 1. ETH covers
        // both the generic branch and the Thor/Maya-eligible branches (USDC) since they take
        // separate code paths in ethereumProviders().
        val swapKitCoins =
            listOf(
                coin(Chain.Ethereum, "ZZZ", isNative = false), // generic EVM token → evmAggregators
                coin(Chain.Ethereum, "USDC", isNative = false), // isThor && isMaya branch
                coin(Chain.Ethereum, "WBTC", isNative = false), // isThor-only branch
                coin(Chain.Ethereum, "LLD", isNative = false), // isMaya-only branch
                coin(Chain.BscChain, "BNB", isNative = true),
                coin(Chain.BscChain, "ZZZ", isNative = false), // non-thor BSC → evmAggregators
                coin(Chain.Avalanche, "AVAX", isNative = true),
                coin(Chain.Base, "ETH", isNative = true),
                coin(Chain.Optimism, "ETH", isNative = true),
                coin(Chain.Polygon, "POL", isNative = true),
                coin(Chain.Arbitrum, "ETH", isNative = true),
                coin(Chain.Arbitrum, "ARB", isNative = false), // maya-eligible Arbitrum token
                coin(Chain.Solana, "SOL", isNative = true),
                coin(Chain.Solana, "USDC", isNative = false),
                coin(Chain.Bitcoin, "BTC", isNative = true), // BTC PSBT route
                coin(Chain.Litecoin, "LTC", isNative = true), // LTC segwit PSBT route
                coin(Chain.Dogecoin, "DOGE", isNative = true), // DOGE legacy P2PKH route
                coin(Chain.BitcoinCash, "BCH", isNative = true), // BCH legacy P2PKH (FORKID) route
                coin(Chain.Dash, "DASH", isNative = true), // DASH legacy P2PKH route
                coin(Chain.Zcash, "ZEC", isNative = true), // ZEC Sapling-v4 transparent route
                coin(Chain.Tron, "TRX", isNative = true), // TRON TronWeb route
                coin(Chain.Tron, "USDT", isNative = false), // TRC-20 → TRON route
                coin(Chain.Sui, "SUI", isNative = true), // SUI PTB route
                coin(Chain.Cardano, "ADA", isNative = true), // Cardano CBOR / deposit route
                coin(Chain.Ton, "TON", isNative = true), // TON native deposit route
                coin(Chain.Ripple, "XRP", isNative = true), // XRP deposit-only route
            )

        swapKitCoins.forEach { c ->
            assertTrue(
                SwapProvider.SWAPKIT in table.providersFor(c),
                "Expected SWAPKIT for ${c.chain}/${c.ticker} but got ${table.providersFor(c)}",
            )
        }
    }

    @Test
    fun `SwapKit is not offered on chains it does not route in Phase 1`() {
        // Boundary guard the other way: SWAPKIT must NOT leak onto chains absent from its branches,
        // otherwise the source would mint a garbage asset id and 500 from the proxy.
        val nonSwapKitCoins =
            listOf(
                coin(Chain.ZkSync, "ETH", isNative = true),
                coin(Chain.Mantle, "MNT", isNative = true),
                coin(Chain.Blast, "ETH", isNative = true),
                coin(Chain.CronosChain, "CRO", isNative = true),
                coin(Chain.GaiaChain, "ATOM", isNative = true),
                coin(Chain.ThorChain, "RUNE", isNative = true),
                coin(Chain.MayaChain, "CACAO", isNative = true),
                coin(Chain.Hyperliquid, "HYPE", isNative = true),
                coin(Chain.Polkadot, "DOT", isNative = true),
            )

        nonSwapKitCoins.forEach { c ->
            assertFalse(
                SwapProvider.SWAPKIT in table.providersFor(c),
                "Did not expect SWAPKIT for ${c.chain}/${c.ticker} but got ${table.providersFor(c)}",
            )
        }
    }

    @Test
    fun `KyberSwap is offered alongside the EVM aggregators on Optimism and Polygon`() {
        val expected =
            setOf(SwapProvider.ONEINCH, SwapProvider.LIFI, SwapProvider.KYBER, SwapProvider.SWAPKIT)

        listOf(Chain.Optimism, Chain.Polygon).forEach { chain ->
            assertEquals(
                expected,
                table.providersFor(coin(chain, "ZZZ", isNative = false)),
                "Expected the full evmAggregators set (incl. KYBER) on $chain",
            )
        }
    }

    @Test
    fun `KyberSwap is dropped on a cross-chain Optimism to Polygon pair`() {
        val eligible =
            table.eligibleProvidersFor(
                srcToken = coin(Chain.Optimism, "ZZZ", isNative = false),
                dstToken = coin(Chain.Polygon, "YYY", isNative = false),
            )

        assertTrue(SwapProvider.SWAPKIT in eligible, "SWAPKIT dropped on cross-chain: $eligible")
        assertTrue(SwapProvider.LIFI in eligible, "LIFI dropped on cross-chain: $eligible")
        assertFalse(SwapProvider.KYBER in eligible, "KYBER (sameChainOnly) leaked: $eligible")
        assertFalse(SwapProvider.ONEINCH in eligible, "ONEINCH (sameChainOnly) leaked: $eligible")
    }

    @Test
    fun `SwapKit-wired chains are marked swap-supported so the Swap action button shows`() {
        // ChainTokensViewModel.canSwap reads Chain.isSwapSupported to show the Swap button on the
        // account screen. A chain can offer SWAPKIT in the provider table yet stay invisible to the
        // user if it is missing from isSwapSupported — the Sui regression that hid the button while
        // iOS showed it. Pin every SwapKit-wired native chain here.
        listOf(
                Chain.Bitcoin,
                Chain.Litecoin,
                Chain.Dogecoin,
                Chain.BitcoinCash,
                Chain.Dash,
                Chain.Zcash,
                Chain.Tron,
                Chain.Sui,
                Chain.Cardano,
                Chain.Ton,
                Chain.Ripple,
            )
            .forEach { chain ->
                assertTrue(
                    chain.isSwapSupported,
                    "$chain offers SWAPKIT but is not marked isSwapSupported — Swap button would hide",
                )
            }
    }

    @Test
    fun `SwapKit survives cross-chain filtering on an EVM-to-EVM pair`() {
        // Ethereum→BSC: both branches contain ONEINCH/KYBER (sameChainOnly) and SWAPKIT. The
        // cross-chain filter must drop the same-chain-only aggregators but keep SWAPKIT — pinning
        // that SWAPKIT is NOT in sameChainOnly.
        val eligible =
            table.eligibleProvidersFor(
                srcToken = coin(Chain.Ethereum, "ZZZ", isNative = false),
                dstToken = coin(Chain.BscChain, "ZZZ", isNative = false),
            )

        assertTrue(SwapProvider.SWAPKIT in eligible, "SWAPKIT dropped on cross-chain: $eligible")
        assertFalse(SwapProvider.ONEINCH in eligible, "ONEINCH (sameChainOnly) leaked: $eligible")
        assertFalse(SwapProvider.KYBER in eligible, "KYBER (sameChainOnly) leaked: $eligible")
    }

    @Test
    fun `SwapKit survives cross-chain filtering on an EVM-to-Solana pair`() {
        val eligible =
            table.eligibleProvidersFor(
                srcToken = coin(Chain.Ethereum, "ZZZ", isNative = false),
                dstToken = coin(Chain.Solana, "SOL", isNative = true),
            )

        assertTrue(SwapProvider.SWAPKIT in eligible, "SWAPKIT dropped on cross-chain: $eligible")
    }

    @Test
    fun `SwapKit is eligible alongside the same-chain aggregators on a same-chain pair`() {
        // Same-chain ETH→ETH: nothing is filtered, so SWAPKIT coexists with ONEINCH/KYBER.
        val eligible =
            table.eligibleProvidersFor(
                srcToken = coin(Chain.Ethereum, "ZZZ", isNative = false),
                dstToken = coin(Chain.Ethereum, "YYY", isNative = false),
            )

        assertTrue(SwapProvider.SWAPKIT in eligible, "SWAPKIT missing same-chain: $eligible")
        assertTrue(SwapProvider.ONEINCH in eligible, "ONEINCH missing same-chain: $eligible")
    }

    private fun coin(chain: Chain, ticker: String, isNative: Boolean, contract: String = "") =
        Coin(
            chain = chain,
            ticker = ticker,
            logo = "",
            address = "addr",
            decimal = 18,
            hexPublicKey = "pub",
            priceProviderID = ticker.lowercase(),
            contractAddress = if (isNative) "" else contract.ifBlank { "0xcontract" },
            isNativeToken = isNative,
        )
}

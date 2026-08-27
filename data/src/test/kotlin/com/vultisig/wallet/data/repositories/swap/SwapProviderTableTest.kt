package com.vultisig.wallet.data.repositories.swap

import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.SwapProvider
import com.vultisig.wallet.data.models.isSwapSupported
import com.vultisig.wallet.data.models.swapAssetName
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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

    private val table = SwapProviderTableImpl(EmptySwapPoolEligibility)

    @Test
    fun `SwapKit is offered on every chain the wallet can receive on`() {
        // Each (chain, ticker, native) pair is a chain the wallet holds a SwapKit-reachable asset
        // on. ETH covers both the generic branch and the Thor/Maya-eligible branches (USDC) since
        // they take separate code paths in ethereumProviders().
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
                // Chains that carried no SwapKit row before the table stopped allowlisting. They
                // are offered on the strength of SwapKit having a name for them; whether it
                // actually routes them is the `/providers` cache's call at quote time.
                coin(Chain.Robinhood, "ETH", isNative = true), // 4663 → HOOD.*
                coin(Chain.Hyperliquid, "HYPE", isNative = true), // 999 → HYPEREVM.*
            )

        swapKitCoins.forEach { c ->
            assertTrue(
                SwapProvider.SWAPKIT in table.providersFor(c),
                "Expected SWAPKIT for ${c.chain}/${c.ticker} but got ${table.providersFor(c)}",
            )
        }
    }

    @Test
    fun `SwapKit is not offered on chains the wallet cannot receive a swap on`() {
        // Boundary guard the other way. Opening the chain list did not make it unbounded: a chain
        // with no SwapKit-reachable account here would mint a garbage asset id and 500 from the
        // proxy. Sei is the one EVM network excluded outright — the wallet holds it but does not
        // swap on it at all. ZkSync, Mantle, Blast and Cronos are excluded for the other reason:
        // SwapKit has no asset-identifier spelling for them, so a quote could never be addressed
        // and offering one would only cost the pair its immediate "no route" answer.
        val nonSwapKitCoins =
            listOf(
                coin(Chain.Sei, "SEI", isNative = true),
                coin(Chain.ZkSync, "ETH", isNative = true),
                coin(Chain.Mantle, "MNT", isNative = true),
                coin(Chain.Blast, "ETH", isNative = true),
                coin(Chain.CronosChain, "CRO", isNative = true),
                coin(Chain.GaiaChain, "ATOM", isNative = true),
                coin(Chain.ThorChain, "RUNE", isNative = true),
                coin(Chain.MayaChain, "CACAO", isNative = true),
                coin(Chain.Kujira, "KUJI", isNative = true),
                coin(Chain.Polkadot, "DOT", isNative = true),
                coin(Chain.Qbtc, "QBTC", isNative = true),
                coin(Chain.Bittensor, "TAO", isNative = true),
            )

        nonSwapKitCoins.forEach { c ->
            assertFalse(
                SwapProvider.SWAPKIT in table.providersFor(c),
                "Did not expect SWAPKIT for ${c.chain}/${c.ticker} but got ${table.providersFor(c)}",
            )
        }
    }

    @Test
    fun `Robinhood is a SwapKit destination but never a SwapKit source`() {
        // Blockaid does not index 4663, so an EVM route cannot be reputation-checked before it is
        // signed from Robinhood. Destination-only rather than dropped: the pair keeps SwapKit in
        // one direction, and Robinhood's own aggregators are untouched in both.
        val hood = coin(Chain.Robinhood, "ETH", isNative = true)
        val eth = coin(Chain.Ethereum, "ETH", isNative = true)

        assertTrue(SwapProvider.SWAPKIT in table.providersFor(hood))
        assertTrue(
            SwapProvider.SWAPKIT in table.eligibleProvidersFor(eth, hood),
            "Expected SwapKit into Robinhood",
        )
        assertFalse(
            SwapProvider.SWAPKIT in table.eligibleProvidersFor(hood, eth),
            "Did not expect SwapKit out of Robinhood",
        )
    }

    @Test
    fun `a blocked SwapKit source keeps every other provider for the pair`() {
        // The rule is "skip SwapKit", never "fail the pair".
        val hood = coin(Chain.Robinhood, "ZZZ", isNative = false)
        val eth = coin(Chain.Ethereum, "ZZZ", isNative = false)

        val eligible = table.eligibleProvidersFor(hood, eth)

        assertFalse(SwapProvider.SWAPKIT in eligible)
        assertEquals(
            listOf(SwapProvider.LIFI),
            eligible,
            "Cross-chain drops the same-chain-only aggregators, but LI.FI must survive",
        )
    }

    @Test
    fun `HyperEVM quotes SwapKit in both directions`() {
        // Unlike Robinhood, Blockaid indexes HyperEVM as `hyperevm`, so it can originate a route.
        val hype = coin(Chain.Hyperliquid, "HYPE", isNative = true)
        val eth = coin(Chain.Ethereum, "ETH", isNative = true)

        assertTrue(SwapProvider.SWAPKIT in table.eligibleProvidersFor(hype, eth))
        assertTrue(SwapProvider.SWAPKIT in table.eligibleProvidersFor(eth, hype))
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
    fun `Arbitrum offers 1inch and KyberSwap, keeping Maya for Maya-routable tokens`() {
        assertEquals(
            setOf(
                SwapProvider.MAYA,
                SwapProvider.ONEINCH,
                SwapProvider.LIFI,
                SwapProvider.KYBER,
                SwapProvider.SWAPKIT,
            ),
            table.providersFor(coin(Chain.Arbitrum, "ARB", isNative = false)),
            "Maya-routable Arbitrum token should keep MAYA and gain the EVM aggregators",
        )
        assertEquals(
            setOf(
                SwapProvider.ONEINCH,
                SwapProvider.LIFI,
                SwapProvider.KYBER,
                SwapProvider.SWAPKIT,
            ),
            table.providersFor(coin(Chain.Arbitrum, "ZZZ", isNative = false)),
            "Generic Arbitrum token should get the full evmAggregators set",
        )
    }

    @Test
    fun `Robinhood offers 1inch, LiFi, Kyber and SwapKit`() {
        // The first three are live-confirmed on 4663; 1inch and Kyber are sameChainOnly, so a
        // cross-chain pair must fall back to LiFi alone — SwapKit is dropped there for a different
        // reason, the source-side reputation gate. No 1inch-set drift.
        val expected =
            setOf(SwapProvider.ONEINCH, SwapProvider.LIFI, SwapProvider.KYBER, SwapProvider.SWAPKIT)
        listOf(
                coin(Chain.Robinhood, "ETH", isNative = true),
                coin(Chain.Robinhood, "AAPL", isNative = false),
            )
            .forEach { c ->
                assertEquals(expected, table.providersFor(c), "Provider set for ${c.ticker}")
            }

        val crossChain =
            table.eligibleProvidersFor(
                srcToken = coin(Chain.Robinhood, "ETH", isNative = true),
                dstToken = coin(Chain.Ethereum, "ETH", isNative = true),
            )
        assertEquals(
            setOf(SwapProvider.LIFI),
            crossChain.toSet(),
            "Cross-chain from Robinhood must drop the same-chain-only aggregators",
        )
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
    fun `GaiaChain offers THORChain for native ATOM but nothing for IBC tokens`() {
        // THORChain's only Cosmos Hub pool is GAIA.ATOM. An IBC token like rKUJI would be quoted
        // as `GAIA.rKUJI-ibc/...`, which Thornode rejects with "bad to asset" — it must get an
        // empty provider set so the pair reads "Swap route not available" up front (#5113).
        assertEquals(
            setOf(SwapProvider.THORCHAIN),
            table.providersFor(coin(Chain.GaiaChain, "ATOM", isNative = true)),
            "Native ATOM must keep its THORChain route",
        )
        assertEquals(
            emptySet<SwapProvider>(),
            table.providersFor(
                coin(Chain.GaiaChain, "rKUJI", isNative = false, contract = "ibc/50A69DC508AC")
            ),
            "A Cosmos IBC token has no THORChain pool and must get no providers",
        )
    }

    @Test
    fun `Kujira offers no providers now that Maya has delisted every KUJI pool`() {
        // MayaChain was Kujira's only provider and no KUJI.* pool remains, so KUJI→USK and every
        // cross-chain KUJI pair must resolve to no providers — the pipeline then raises
        // SwapIsNotSupported → "Swap route not available" instead of a doomed quote (#5472).
        listOf(
                coin(Chain.Kujira, "KUJI", isNative = true),
                coin(Chain.Kujira, "USK", isNative = false, contract = "factory/kujira1/uusk"),
            )
            .forEach { c ->
                assertEquals(
                    emptySet<SwapProvider>(),
                    table.providersFor(c),
                    "Kujira must offer no providers for ${c.ticker}",
                )
            }

        assertEquals(
            emptyList<SwapProvider>(),
            table.eligibleProvidersFor(
                srcToken = coin(Chain.Kujira, "KUJI", isNative = true),
                dstToken = coin(Chain.Kujira, "USK", isNative = false, contract = "factory/uusk"),
            ),
            "Same-chain KUJI→USK must have no eligible provider",
        )
    }

    @Test
    fun `MayaChain keeps its MAYA route after Kujira was split out`() {
        assertEquals(
            setOf(SwapProvider.MAYA),
            table.providersFor(coin(Chain.MayaChain, "CACAO", isNative = true)),
        )
    }

    @Test
    fun `live THORChain pool adds a GaiaChain token route the static fallback omits`() {
        val liveTable = SwapProviderTableImpl(FakeEligibility(thor = setOf("GAIA.RKUJI")))
        assertEquals(
            setOf(SwapProvider.THORCHAIN),
            liveTable.providersFor(
                coin(Chain.GaiaChain, "rKUJI", isNative = false, contract = "ibc/50A69DC508AC")
            ),
            "An Available GAIA pool must re-enable the THORChain route",
        )
    }

    @Test
    fun `live Maya pool adds MAYA for ETH USDT that the static fallback omits`() {
        // USDT is not in the static mayaEthTokens fallback, so with no live pools the Ethereum
        // branch never offers MAYA for it.
        assertFalse(
            SwapProvider.MAYA in table.providersFor(coin(Chain.Ethereum, "USDT", isNative = false)),
            "MAYA should be absent for ETH.USDT under the static-only fallback",
        )

        // Once the live MayaChain pool for ETH.USDT reports Available, the table must add MAYA —
        // the issue's concrete trigger (#4975).
        val liveTable = SwapProviderTableImpl(FakeEligibility(maya = setOf("ETH.USDT")))
        assertTrue(
            SwapProvider.MAYA in
                liveTable.providersFor(coin(Chain.Ethereum, "USDT", isNative = false)),
            "MAYA should be offered for ETH.USDT once its Maya pool is Available",
        )
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

    @Test
    fun `Jupiter is offered for on-Solana pairs and dropped for cross-chain Solana pairs`() {
        // Jupiter is Solana-only and same-chain (#5053): it must be a candidate for SOL↔SPL and
        // SPL↔SPL, but never for a cross-chain pair where it can't route. The cross-chain drop
        // happens via the intersection — Jupiter is absent from every non-Solana chain's set.
        val sol = coin(Chain.Solana, "SOL", isNative = true)
        val splUsdc = coin(Chain.Solana, "USDC", isNative = false, contract = "EPjF")
        val splBonk = coin(Chain.Solana, "BONK", isNative = false, contract = "DezX")
        val btc = coin(Chain.Bitcoin, "BTC", isNative = true)
        val eth = coin(Chain.Ethereum, "ETH", isNative = true)

        assertTrue(
            SwapProvider.JUPITER in table.eligibleProvidersFor(sol, splUsdc),
            "Jupiter must be eligible for SOL↔SPL",
        )
        assertTrue(
            SwapProvider.JUPITER in table.eligibleProvidersFor(splUsdc, splBonk),
            "Jupiter must be eligible for SPL↔SPL",
        )
        assertFalse(
            SwapProvider.JUPITER in table.eligibleProvidersFor(sol, btc),
            "Jupiter must be dropped for native SOL → BTC",
        )
        assertFalse(
            SwapProvider.JUPITER in table.eligibleProvidersFor(splUsdc, eth),
            "Jupiter must be dropped for SPL → ETH",
        )
    }

    @Test
    fun `native SOL cross-chain keeps THORChain and drops Jupiter`() {
        // Native SOL → BTC/ETH must route through THORChain (THORChain SOL support is wired), with
        // Jupiter excluded as a cross-chain candidate (#5053).
        val sol = coin(Chain.Solana, "SOL", isNative = true)

        listOf(
                coin(Chain.Bitcoin, "BTC", isNative = true),
                coin(Chain.Ethereum, "ETH", isNative = true),
            )
            .forEach { dst ->
                val eligible = table.eligibleProvidersFor(sol, dst)
                assertTrue(
                    SwapProvider.THORCHAIN in eligible,
                    "THORChain must remain eligible for native SOL → ${dst.chain}: $eligible",
                )
                assertFalse(
                    SwapProvider.JUPITER in eligible,
                    "Jupiter must not be eligible for native SOL → ${dst.chain}: $eligible",
                )
            }
    }

    /**
     * In-memory eligibility whose live `CHAIN.TICKER` sets stand in for fetched Available pools.
     */
    private class FakeEligibility(
        private val thor: Set<String> = emptySet(),
        private val maya: Set<String> = emptySet(),
    ) : SwapPoolEligibilityRepository {
        override fun isThorEligible(chain: Chain, ticker: String): Boolean =
            key(chain, ticker) in thor

        override fun isMayaEligible(chain: Chain, ticker: String): Boolean =
            key(chain, ticker) in maya

        override val eligibilityVersion: StateFlow<Int> = MutableStateFlow(1)

        override suspend fun refresh() = Unit

        private fun key(chain: Chain, ticker: String) =
            "${chain.swapAssetName().uppercase()}.${ticker.uppercase()}"
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

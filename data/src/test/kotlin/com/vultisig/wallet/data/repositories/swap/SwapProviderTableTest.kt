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

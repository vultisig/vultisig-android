package com.vultisig.wallet.data.repositories.swap

import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.SwapProvider
import javax.inject.Inject

interface SwapProviderTable {
    fun providersFor(coin: Coin): Set<SwapProvider>

    fun eligibleProvidersFor(srcToken: Coin, dstToken: Coin): List<SwapProvider>
}

internal class SwapProviderTableImpl
@Inject
constructor(private val poolEligibility: SwapPoolEligibilityRepository) : SwapProviderTable {

    // Static *Tokens sets are the cold-start / offline fallback only. Live eligibility comes from
    // [poolEligibility]; a fetched Available pool can only ADD a route on top of these sets.
    private val thorEthTokens =
        setOf(
            "ETH",
            "USDT",
            "USDC",
            "WBTC",
            "THOR",
            "XRUNE",
            "DAI",
            "LUSD",
            "GUSD",
            "VTHOR",
            "USDP",
            "LINK",
            "TGT",
            "AAVE",
            "FOX",
            "DPI",
            "SNX",
            "YFI",
        )
    private val thorBscTokens = setOf("BNB", "USDT", "USDC")
    private val thorGaiaTokens = setOf("ATOM")
    private val thorAvaxTokens = setOf("AVAX", "USDC", "USDT", "SOL")
    private val thorBaseTokens = setOf("ETH", "CBBTC", "USDC", "VVV")
    private val mayaEthTokens = setOf("ETH", "USDC", "LLD")
    private val mayaArbTokens =
        setOf(
            "ETH",
            "ARB",
            "WSTETH",
            "LINK",
            "PEPE",
            "WBTC",
            "GLD",
            "TGT",
            "LEO",
            "YUM",
            "USDC",
            "USDT",
            "DAI",
        )

    // SwapKit is absent from every set below on purpose: [providersFor] appends it centrally for
    // any chain [SwapKitCapability.canReceiveOn] allows, so a newly supported network never needs a
    // row here just to be offered a SwapKit quote.
    private val evmAggregators = setOf(SwapProvider.ONEINCH, SwapProvider.LIFI, SwapProvider.KYBER)
    private val thorchainPlusEvmAggregators =
        setOf(SwapProvider.THORCHAIN, SwapProvider.ONEINCH, SwapProvider.LIFI, SwapProvider.KYBER)
    private val mayaPlusEvmAggregators =
        setOf(SwapProvider.MAYA, SwapProvider.ONEINCH, SwapProvider.LIFI, SwapProvider.KYBER)

    /** Providers that only quote same-chain swaps; filtered out for cross-chain pairs. */
    private val sameChainOnly = setOf(SwapProvider.ONEINCH, SwapProvider.KYBER)

    override fun providersFor(coin: Coin): Set<SwapProvider> {
        val natural = naturalProvidersFor(coin)
        return if (SwapKitCapability.canReceiveOn(coin.chain)) natural + SwapProvider.SWAPKIT
        else natural
    }

    /**
     * The chain's own provider set, before SwapKit is appended by [providersFor]. THORChain / Maya
     * eligibility is still per-token here; SwapKit's is not a table concern at all.
     */
    private fun naturalProvidersFor(coin: Coin): Set<SwapProvider> {
        val ticker = coin.ticker.uppercase()
        return when (coin.chain) {
            Chain.MayaChain -> setOf(SwapProvider.MAYA)

            Chain.Dash -> setOf(SwapProvider.MAYA)

            Chain.Ethereum -> ethereumProviders(ticker)

            Chain.BscChain ->
                if (isThorEligible(Chain.BscChain, ticker, thorBscTokens))
                    thorchainPlusEvmAggregators
                else evmAggregators

            Chain.Avalanche ->
                if (isThorEligible(Chain.Avalanche, ticker, thorAvaxTokens))
                    thorchainPlusEvmAggregators
                else evmAggregators

            // 1inch (#5256) and KyberSwap (#5255) are same-chain aggregators live-confirmed on
            // Base; iOS and the SDK both offer them here. This makes Base match the BSC/Avalanche
            // EVM arm.
            Chain.Base ->
                if (isThorEligible(Chain.Base, ticker, thorBaseTokens)) thorchainPlusEvmAggregators
                else evmAggregators

            Chain.Optimism,
            Chain.Polygon -> evmAggregators

            Chain.ZkSync -> setOf(SwapProvider.ONEINCH, SwapProvider.LIFI)

            Chain.Mantle -> setOf(SwapProvider.LIFI, SwapProvider.KYBER)

            // All three live-confirmed on 4663; 1inch /swap returns executable calldata to its
            // deployed router (0x5a70…89c7). SwapKit rides along via [providersFor], destination
            // only — Blockaid does not index 4663, so [SwapKitCapability.canQuoteFrom] refuses it
            // as a source.
            Chain.Robinhood -> setOf(SwapProvider.ONEINCH, SwapProvider.LIFI, SwapProvider.KYBER)

            Chain.ThorChain -> setOf(SwapProvider.THORCHAIN, SwapProvider.MAYA)
            Chain.Bitcoin -> setOf(SwapProvider.THORCHAIN, SwapProvider.MAYA)

            // THORChain's only Cosmos Hub pool is native ATOM. IBC/factory tokens (e.g. rKUJI)
            // would be sent as `GAIA.<TICKER>-ibc/...`, which Thornode rejects with "bad to asset"
            // (#5113) — offer them no providers instead of a guaranteed-to-fail quote. A live
            // Available pool can still add a route for a listed token.
            Chain.GaiaChain ->
                if (isThorEligible(Chain.GaiaChain, ticker, thorGaiaTokens))
                    setOf(SwapProvider.THORCHAIN)
                else emptySet()

            Chain.Dogecoin,
            Chain.BitcoinCash,
            Chain.Litecoin -> setOf(SwapProvider.THORCHAIN)

            Chain.Zcash -> setOf(SwapProvider.MAYA)

            Chain.Arbitrum ->
                if (isMayaEligible(Chain.Arbitrum, ticker, mayaArbTokens)) mayaPlusEvmAggregators
                else evmAggregators

            Chain.Blast,
            Chain.CronosChain -> setOf(SwapProvider.LIFI)

            Chain.Solana ->
                if (coin.isNativeToken)
                    setOf(SwapProvider.THORCHAIN, SwapProvider.JUPITER, SwapProvider.LIFI)
                else setOf(SwapProvider.JUPITER, SwapProvider.LIFI)

            Chain.Ripple -> setOf(SwapProvider.THORCHAIN)

            Chain.Tron -> setOf(SwapProvider.THORCHAIN)

            // TON, SUI and Cardano have no native Thor/Maya route on Android, so SwapKit — appended
            // by [providersFor] — is the only provider they ever carry.
            Chain.Ton,
            Chain.Sui,
            Chain.Cardano -> emptySet()

            Chain.Hyperliquid -> setOf(SwapProvider.LIFI)

            Chain.Polkadot,
            Chain.Bittensor,
            Chain.Dydx,
            Chain.Osmosis,
            Chain.Terra,
            Chain.TerraClassic,
            Chain.Noble,
            Chain.Akash,
            Chain.Sei,
            Chain.Qbtc -> emptySet()
        }
    }

    override fun eligibleProvidersFor(srcToken: Coin, dstToken: Coin): List<SwapProvider> {
        val shared = providersFor(srcToken).intersect(providersFor(dstToken))
        val crossChain = srcToken.chain != dstToken.chain
        val bothThorChain = srcToken.chain == Chain.ThorChain && dstToken.chain == Chain.ThorChain
        // SwapKit eligibility is directional (iOS SwapCoinsResolver.resolveAllProviders): a chain
        // the app can receive on but cannot reputation-check stays a valid SwapKit *destination*
        // while being refused as a source. Dropping only SwapKit here — never the whole pair —
        // leaves 1inch / Kyber / LI.FI / THOR / Maya standing on such a source.
        val swapKitSourceBlocked = !SwapKitCapability.canQuoteFrom(srcToken.chain)
        return shared.filter { provider ->
            (!crossChain || provider !in sameChainOnly) &&
                !(bothThorChain && provider == SwapProvider.MAYA) &&
                !(swapKitSourceBlocked && provider == SwapProvider.SWAPKIT)
        }
    }

    /**
     * True if [ticker] on [chain] is THORChain-routable via a live Available pool or the
     * [fallback].
     */
    private fun isThorEligible(chain: Chain, ticker: String, fallback: Set<String>): Boolean =
        ticker in fallback || poolEligibility.isThorEligible(chain, ticker)

    /**
     * True if [ticker] on [chain] is MayaChain-routable via a live Available pool or the
     * [fallback].
     */
    private fun isMayaEligible(chain: Chain, ticker: String, fallback: Set<String>): Boolean =
        ticker in fallback || poolEligibility.isMayaEligible(chain, ticker)

    private fun ethereumProviders(ticker: String): Set<SwapProvider> {
        val isThor = isThorEligible(Chain.Ethereum, ticker, thorEthTokens)
        val isMaya = isMayaEligible(Chain.Ethereum, ticker, mayaEthTokens)
        // SwapKit is included in every Ethereum branch: the per-token-pair eligibility is
        // negotiated downstream at `/v3/quote` time (and gated by the SwapKit feature flag +
        // provider cache inside SwapKitQuoteSource), so the table only needs to surface SwapKit
        // wherever the existing EVM aggregators show up. Without this, ETH/USDC/USDT and the
        // other Thor/Maya-eligible Ethereum tokens silently lose SwapKit as a candidate even
        // though the cache enables Ethereum.
        return when {
            isThor && isMaya ->
                setOf(
                    SwapProvider.THORCHAIN,
                    SwapProvider.ONEINCH,
                    SwapProvider.LIFI,
                    SwapProvider.KYBER,
                    SwapProvider.SWAPKIT,
                    SwapProvider.MAYA,
                )

            isThor ->
                setOf(
                    SwapProvider.THORCHAIN,
                    SwapProvider.ONEINCH,
                    SwapProvider.LIFI,
                    SwapProvider.KYBER,
                    SwapProvider.SWAPKIT,
                )

            isMaya ->
                setOf(
                    SwapProvider.ONEINCH,
                    SwapProvider.LIFI,
                    SwapProvider.MAYA,
                    SwapProvider.KYBER,
                    SwapProvider.SWAPKIT,
                )

            else -> evmAggregators
        }
    }
}

package com.vultisig.wallet.data.repositories.swap

import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.SwapProvider
import javax.inject.Inject

interface SwapProviderTable {
    fun providersFor(coin: Coin): Set<SwapProvider>

    fun eligibleProvidersFor(srcToken: Coin, dstToken: Coin): List<SwapProvider>
}

internal class SwapProviderTableImpl @Inject constructor() : SwapProviderTable {

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

    private val evmAggregators =
        setOf(SwapProvider.ONEINCH, SwapProvider.LIFI, SwapProvider.KYBER, SwapProvider.SWAPKIT)
    private val thorchainPlusEvmAggregators =
        setOf(
            SwapProvider.THORCHAIN,
            SwapProvider.ONEINCH,
            SwapProvider.LIFI,
            SwapProvider.KYBER,
            SwapProvider.SWAPKIT,
        )

    /** Providers that only quote same-chain swaps; filtered out for cross-chain pairs. */
    private val sameChainOnly = setOf(SwapProvider.ONEINCH, SwapProvider.KYBER)

    override fun providersFor(coin: Coin): Set<SwapProvider> {
        val ticker = coin.ticker.uppercase()
        return when (coin.chain) {
            Chain.MayaChain,
            Chain.Dash,
            Chain.Kujira -> setOf(SwapProvider.MAYA)

            Chain.Ethereum -> ethereumProviders(ticker)

            Chain.BscChain ->
                if (ticker in thorBscTokens) thorchainPlusEvmAggregators else evmAggregators

            Chain.Avalanche ->
                if (ticker in thorAvaxTokens) thorchainPlusEvmAggregators else evmAggregators

            Chain.Base ->
                if (ticker in thorBaseTokens)
                    setOf(SwapProvider.LIFI, SwapProvider.THORCHAIN, SwapProvider.SWAPKIT)
                else setOf(SwapProvider.LIFI, SwapProvider.SWAPKIT)

            Chain.Optimism,
            Chain.Polygon -> setOf(SwapProvider.ONEINCH, SwapProvider.LIFI, SwapProvider.SWAPKIT)

            Chain.ZkSync -> setOf(SwapProvider.ONEINCH, SwapProvider.LIFI)

            Chain.Mantle -> setOf(SwapProvider.LIFI, SwapProvider.KYBER)

            Chain.ThorChain -> setOf(SwapProvider.THORCHAIN, SwapProvider.MAYA)
            Chain.Bitcoin -> setOf(SwapProvider.THORCHAIN, SwapProvider.MAYA, SwapProvider.SWAPKIT)

            Chain.Dogecoin,
            Chain.BitcoinCash,
            Chain.Litecoin,
            Chain.GaiaChain -> setOf(SwapProvider.THORCHAIN)

            Chain.Zcash -> setOf(SwapProvider.MAYA)

            Chain.Arbitrum ->
                if (ticker in mayaArbTokens)
                    setOf(SwapProvider.LIFI, SwapProvider.MAYA, SwapProvider.SWAPKIT)
                else setOf(SwapProvider.LIFI, SwapProvider.SWAPKIT)

            Chain.Blast,
            Chain.CronosChain -> setOf(SwapProvider.LIFI)

            Chain.Solana ->
                if (coin.isNativeToken)
                    setOf(
                        SwapProvider.THORCHAIN,
                        SwapProvider.JUPITER,
                        SwapProvider.LIFI,
                        SwapProvider.SWAPKIT,
                    )
                else setOf(SwapProvider.JUPITER, SwapProvider.LIFI, SwapProvider.SWAPKIT)

            // SwapKit XRP is deposit-only — no signer; the native RippleHelper builds the Payment
            // to
            // SwapKit's deposit r-address. Mirrors iOS' `.ripple → [.thorchain, .swapkit]`.
            Chain.Ripple -> setOf(SwapProvider.THORCHAIN, SwapProvider.SWAPKIT)

            // SwapKit TRON routes are signed by SwapKitTronSigner (TronWeb object → sha256 of
            // raw_data_hex). Mirrors iOS' `.tron → [.thorchain, .swapkit]`.
            Chain.Tron -> setOf(SwapProvider.THORCHAIN, SwapProvider.SWAPKIT)

            Chain.Hyperliquid -> setOf(SwapProvider.LIFI)

            // SwapKit SUI routes are signed by SwapKitSuiSigner (Blake2b-32 of the intent-prefixed
            // PTB, Ed25519 envelope). Mirrors iOS' `.sui → [.swapkit]`.
            Chain.Sui -> setOf(SwapProvider.SWAPKIT)

            Chain.Polkadot,
            Chain.Bittensor,
            Chain.Dydx,
            Chain.Ton,
            Chain.Osmosis,
            Chain.Terra,
            Chain.TerraClassic,
            Chain.Noble,
            Chain.Akash,
            Chain.Cardano,
            Chain.Sei,
            Chain.Qbtc -> emptySet()
        }
    }

    override fun eligibleProvidersFor(srcToken: Coin, dstToken: Coin): List<SwapProvider> {
        val shared = providersFor(srcToken).intersect(providersFor(dstToken))
        val crossChain = srcToken.chain != dstToken.chain
        val bothThorChain = srcToken.chain == Chain.ThorChain && dstToken.chain == Chain.ThorChain
        return shared.filter { provider ->
            (!crossChain || provider !in sameChainOnly) &&
                !(bothThorChain && provider == SwapProvider.MAYA)
        }
    }

    private fun ethereumProviders(ticker: String): Set<SwapProvider> {
        val isThor = ticker in thorEthTokens
        val isMaya = ticker in mayaEthTokens
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

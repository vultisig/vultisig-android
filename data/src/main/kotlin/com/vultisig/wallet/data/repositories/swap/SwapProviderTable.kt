package com.vultisig.wallet.data.repositories.swap

import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.SwapProvider
import javax.inject.Inject

/**
 * Routing table for swap providers. Maps a chain + ticker to the set of providers that can quote
 * it, and resolves a single provider for a src/dst pair (applying cross-chain restrictions).
 */
interface SwapProviderTable {
    fun providersFor(coin: Coin): Set<SwapProvider>

    fun providerFor(srcToken: Coin, dstToken: Coin): SwapProvider?
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

    private val evmAggregators = setOf(SwapProvider.ONEINCH, SwapProvider.LIFI, SwapProvider.KYBER)
    private val thorchainPlusEvmAggregators =
        setOf(SwapProvider.THORCHAIN, SwapProvider.ONEINCH, SwapProvider.LIFI, SwapProvider.KYBER)

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
                if (ticker in thorBaseTokens) setOf(SwapProvider.LIFI, SwapProvider.THORCHAIN)
                else setOf(SwapProvider.LIFI)

            Chain.Optimism,
            Chain.Polygon,
            Chain.ZkSync -> setOf(SwapProvider.ONEINCH, SwapProvider.LIFI)

            Chain.Mantle -> setOf(SwapProvider.LIFI, SwapProvider.KYBER)

            Chain.ThorChain -> setOf(SwapProvider.THORCHAIN, SwapProvider.MAYA)
            Chain.Bitcoin -> setOf(SwapProvider.THORCHAIN, SwapProvider.MAYA)

            Chain.Dogecoin,
            Chain.BitcoinCash,
            Chain.Litecoin,
            Chain.GaiaChain -> setOf(SwapProvider.THORCHAIN)

            Chain.Zcash -> setOf(SwapProvider.MAYA)

            Chain.Arbitrum ->
                if (ticker in mayaArbTokens) setOf(SwapProvider.LIFI, SwapProvider.MAYA)
                else setOf(SwapProvider.LIFI)

            Chain.Blast,
            Chain.CronosChain -> setOf(SwapProvider.LIFI)

            Chain.Solana ->
                if (coin.isNativeToken)
                    setOf(SwapProvider.THORCHAIN, SwapProvider.JUPITER, SwapProvider.LIFI)
                else setOf(SwapProvider.JUPITER, SwapProvider.LIFI)

            Chain.Ripple,
            Chain.Tron -> setOf(SwapProvider.THORCHAIN)

            Chain.Hyperliquid -> setOf(SwapProvider.LIFI)

            Chain.Polkadot,
            Chain.Bittensor,
            Chain.Dydx,
            Chain.Sui,
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

    override fun providerFor(srcToken: Coin, dstToken: Coin): SwapProvider? {
        val shared = providersFor(srcToken).intersect(providersFor(dstToken))
        val crossChain = srcToken.chain != dstToken.chain
        return shared.firstOrNull { provider -> !crossChain || provider !in sameChainOnly }
    }

    private fun ethereumProviders(ticker: String): Set<SwapProvider> {
        val isThor = ticker in thorEthTokens
        val isMaya = ticker in mayaEthTokens
        return when {
            isThor && isMaya ->
                setOf(
                    SwapProvider.THORCHAIN,
                    SwapProvider.ONEINCH,
                    SwapProvider.LIFI,
                    SwapProvider.KYBER,
                    SwapProvider.MAYA,
                )

            isThor ->
                setOf(
                    SwapProvider.THORCHAIN,
                    SwapProvider.ONEINCH,
                    SwapProvider.LIFI,
                    SwapProvider.KYBER,
                )

            isMaya ->
                setOf(
                    SwapProvider.ONEINCH,
                    SwapProvider.LIFI,
                    SwapProvider.MAYA,
                    SwapProvider.KYBER,
                )

            else -> evmAggregators
        }
    }
}

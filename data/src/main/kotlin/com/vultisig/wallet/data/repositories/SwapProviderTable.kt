package com.vultisig.wallet.data.repositories

import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.SwapProvider

/**
 * Chain + ticker → set of supported [SwapProvider]s. Extracted from [SwapQuoteRepositoryImpl] so
 * the repository stays focused on quote fetching while routing rules live in one inspectable place.
 */
internal object SwapProviderTable {

    private val THOR_ETH_TOKENS =
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
    private val THOR_BSC_TOKENS = setOf("BNB", "USDT", "USDC")
    private val THOR_AVAX_TOKENS = setOf("AVAX", "USDC", "USDT", "SOL")
    private val THOR_BASE_TOKENS = setOf("ETH", "CBBTC", "USDC", "VVV")
    private val MAYA_ETH_TOKENS = setOf("ETH", "USDC", "LLD")
    private val MAYA_ARB_TOKENS =
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

    fun providersFor(coin: Coin): Set<SwapProvider> {
        val ticker = coin.ticker.uppercase()
        return when (coin.chain) {
            Chain.MayaChain,
            Chain.Dash,
            Chain.Kujira -> setOf(SwapProvider.MAYA)

            Chain.Ethereum -> ethereumProviders(ticker)

            Chain.BscChain ->
                if (coin.ticker in THOR_BSC_TOKENS) THORCHAIN_PLUS_EVM_AGGREGATORS
                else EVM_AGGREGATORS

            Chain.Avalanche ->
                if (coin.ticker in THOR_AVAX_TOKENS) THORCHAIN_PLUS_EVM_AGGREGATORS
                else EVM_AGGREGATORS

            Chain.Base ->
                if (ticker in THOR_BASE_TOKENS) setOf(SwapProvider.LIFI, SwapProvider.THORCHAIN)
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
                if (ticker in MAYA_ARB_TOKENS) setOf(SwapProvider.LIFI, SwapProvider.MAYA)
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

    private fun ethereumProviders(ticker: String): Set<SwapProvider> {
        val isThor = ticker in THOR_ETH_TOKENS
        val isMaya = ticker in MAYA_ETH_TOKENS
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

            else -> EVM_AGGREGATORS
        }
    }

    private val EVM_AGGREGATORS = setOf(SwapProvider.ONEINCH, SwapProvider.LIFI, SwapProvider.KYBER)
    private val THORCHAIN_PLUS_EVM_AGGREGATORS =
        setOf(SwapProvider.THORCHAIN, SwapProvider.ONEINCH, SwapProvider.LIFI, SwapProvider.KYBER)
}

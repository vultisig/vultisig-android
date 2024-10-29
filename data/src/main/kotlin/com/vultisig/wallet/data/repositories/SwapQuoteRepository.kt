package com.vultisig.wallet.data.repositories

import com.vultisig.wallet.data.api.LiFiChainApi
import com.vultisig.wallet.data.api.MayaChainApi
import com.vultisig.wallet.data.api.OneInchApi
import com.vultisig.wallet.data.api.ThorChainApi
import com.vultisig.wallet.data.api.errors.SwapException
import com.vultisig.wallet.data.api.models.OneInchSwapQuoteJson
import com.vultisig.wallet.data.api.models.OneInchSwapTxJson
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.SwapProvider
import com.vultisig.wallet.data.models.SwapQuote
import com.vultisig.wallet.data.models.TokenValue
import com.vultisig.wallet.data.models.oneInchChainId
import java.math.BigDecimal
import javax.inject.Inject

interface SwapQuoteRepository {

    suspend fun getSwapQuote(
        dstAddress: String,
        srcToken: Coin,
        dstToken: Coin,
        tokenValue: TokenValue,
        isAffiliate: Boolean,
    ): SwapQuote

    suspend fun getOneInchSwapQuote(
        srcToken: Coin,
        dstToken: Coin,
        tokenValue: TokenValue,
        isAffiliate: Boolean,
    ): OneInchSwapQuoteJson

    suspend fun getMayaSwapQuote(
        dstAddress: String,
        srcToken: Coin,
        dstToken: Coin,
        tokenValue: TokenValue,
        isAffiliate: Boolean,
    ): SwapQuote

    suspend fun getLiFiSwapQuote(
        srcAddress: String,
        dstAddress: String,
        srcToken: Coin,
        dstToken: Coin,
        tokenValue: TokenValue,
    ): OneInchSwapQuoteJson

    fun resolveProvider(
        srcToken: Coin,
        dstToken: Coin,
    ): SwapProvider?

}

internal class SwapQuoteRepositoryImpl @Inject constructor(
    private val thorChainApi: ThorChainApi,
    private val mayaChainApi: MayaChainApi,
    private val oneInchApi: OneInchApi,
    private val liFiChainApi: LiFiChainApi,
) : SwapQuoteRepository {

    override suspend fun getOneInchSwapQuote(
        srcToken: Coin,
        dstToken: Coin,
        tokenValue: TokenValue,
        isAffiliate: Boolean,
    ): OneInchSwapQuoteJson {
        val oneInchQuote = oneInchApi.getSwapQuote(
            chain = srcToken.chain,
            srcTokenContractAddress = srcToken.contractAddress,
            dstTokenContractAddress = dstToken.contractAddress,
            srcAddress = srcToken.address,
            amount = tokenValue.value.toString(),
            isAffiliate = isAffiliate,
        )

        SwapException.handleSwapException(oneInchQuote.error)
        return oneInchQuote
    }

    override suspend fun getMayaSwapQuote(
        dstAddress: String,
        srcToken: Coin,
        dstToken: Coin,
        tokenValue: TokenValue,
        isAffiliate: Boolean,
    ): SwapQuote {
        val thorTokenValue = (tokenValue.decimal * srcToken.thorswapMultiplier).toBigInteger()

        val mayaQuote = mayaChainApi.getSwapQuotes(
            address = dstAddress,
            fromAsset = srcToken.swapAssetName(),
            toAsset = dstToken.swapAssetName(),
            amount = thorTokenValue.toString(),
            interval = srcToken.streamingInterval,
            isAffiliate = isAffiliate,
        )

        SwapException.handleSwapException(mayaQuote.error)

        val tokenFees = mayaQuote.fees.total
            .convertToTokenValue(dstToken)

        val expectedDstTokenValue = mayaQuote.expectedAmountOut
            .convertToTokenValue(dstToken)

        val recommendedMinTokenValue = if (srcToken.chain != Chain.MayaChain) {
            mayaQuote.recommendedMinAmountIn.convertToTokenValue(srcToken)
        } else tokenValue

        return SwapQuote.MayaChain(
            expectedDstValue = expectedDstTokenValue,
            fees = tokenFees,
            data = mayaQuote,
            recommendedMinTokenValue = recommendedMinTokenValue,
        )
    }

    override suspend fun getSwapQuote(
        dstAddress: String,
        srcToken: Coin,
        dstToken: Coin,
        tokenValue: TokenValue,
        isAffiliate: Boolean,
    ): SwapQuote {
        val thorTokenValue = (tokenValue.decimal * srcToken.thorswapMultiplier).toBigInteger()

        val thorQuote = thorChainApi.getSwapQuotes(
            address = dstAddress,
            fromAsset = srcToken.swapAssetName(),
            toAsset = dstToken.swapAssetName(),
            amount = thorTokenValue.toString(),
            interval = "1",
            isAffiliate = isAffiliate,
        )

        SwapException.handleSwapException(thorQuote.error)

        val tokenFees = thorQuote.fees.total
            .convertToTokenValue(dstToken)

        val expectedDstTokenValue = thorQuote.expectedAmountOut
            .convertToTokenValue(dstToken)

        val recommendedMinTokenValue = thorQuote.recommendedMinAmountIn.convertToTokenValue(srcToken)

        return SwapQuote.ThorChain(
            expectedDstValue = expectedDstTokenValue,
            recommendedMinTokenValue = recommendedMinTokenValue,
            fees = tokenFees,
            data = thorQuote,
        )
    }

    @OptIn(ExperimentalStdlibApi::class)
    override suspend fun getLiFiSwapQuote(
        srcAddress: String,
        dstAddress: String,
        srcToken: Coin,
        dstToken: Coin,
        tokenValue: TokenValue,
    ): OneInchSwapQuoteJson {

        val fromToken =
            srcToken.contractAddress.ifEmpty { srcToken.ticker }

        val toToken =
            dstToken.contractAddress.ifEmpty { dstToken.ticker }

        val liFiQuote = liFiChainApi.getSwapQuote(
            fromChain = srcToken.chain.oneInchChainId().toString(),
            toChain = dstToken.chain.oneInchChainId().toString(),
            fromToken = fromToken,
            toToken = toToken,
            fromAmount = tokenValue.value.toString(),
            fromAddress = srcAddress,
            toAddress = dstAddress,
        )
        SwapException.handleSwapException(liFiQuote.message)

        return OneInchSwapQuoteJson(
            dstAmount = liFiQuote.estimate.toAmount,
            tx = OneInchSwapTxJson(
                from = liFiQuote.transactionRequest.from,
                to = liFiQuote.transactionRequest.to,
                data = liFiQuote.transactionRequest.data,
                gas = liFiQuote.transactionRequest.gasLimit.substring(startIndex = 2).hexToLong(),
                value = liFiQuote.transactionRequest.value.substring(startIndex = 2).hexToLong()
                    .toString(),
                gasPrice = liFiQuote.transactionRequest.gasPrice.substring(startIndex = 2)
                    .hexToLong().toString(),
            )
        )
    }

    private val Coin.streamingInterval: String
        get() = when (chain) {
            Chain.MayaChain -> "3"
            Chain.ThorChain -> "1"
            else -> "0"
        }

    private fun String.convertToTokenValue(token: Coin): TokenValue =
        BigDecimal(this)
            .divide(token.thorswapMultiplier)
            .let {
                TokenValue(
                    value = (it.movePointRight(token.decimal)).toBigInteger(),
                    token = token,
                )
            }

    private val Coin.thorswapMultiplier: BigDecimal
        get() = when (chain) {
            Chain.MayaChain -> BigDecimal(1e10)
            else -> BigDecimal(1e8)
        }

    private fun Coin.swapAssetName(): String = if (isNativeToken) {
        if (chain == Chain.GaiaChain) {
            "${chain.swapAssetName()}.ATOM"
        } else {
            // todo it should be chain.ticker (and it seems that they somehow different with Coin::ticker)
            //  maybe it's also the reason why .ATOM hardcoded above there
            "${chain.swapAssetName()}.${ticker}"
        }
    } else {
        "${chain.swapAssetName()}.${ticker}-${contractAddress}"
    }

    override fun resolveProvider(srcToken: Coin, dstToken: Coin): SwapProvider? {
        if (hasNotProvider(srcToken, dstToken)) return null
        return srcToken.swapProviders.intersect(dstToken.swapProviders).firstOrNull()
    }

    private fun hasNotProvider(
        srcToken: Coin,
        dstToken: Coin,
    ) = !srcToken.isNativeToken && srcToken.chain in listOf(
        Chain.Ethereum,
        Chain.Arbitrum
    ) && dstToken.chain == Chain.MayaChain

    private val thorEthTokens = listOf(
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
        "SNX"
    )
    private val thorBscTokens = listOf("BNB", "USDT", "USDC")
    private val thorAvaxTokens = listOf("AVAX", "USDC", "USDT", "SOL")
    private val mayaEthTokens = listOf("ETH")
    private val mayaArbTokens = listOf(
        "ETH",
    )

    private val Coin.swapProviders: Set<SwapProvider>
        get() = when (chain) {
            Chain.MayaChain, Chain.Dash, Chain.Kujira -> setOf(SwapProvider.MAYA)
            Chain.Ethereum -> when {
                ticker in thorEthTokens && ticker in mayaEthTokens -> setOf(
                    SwapProvider.THORCHAIN,
                    SwapProvider.ONEINCH,
                    SwapProvider.LIFI,
                    SwapProvider.MAYA,
                )

                ticker in thorEthTokens -> setOf(
                    SwapProvider.THORCHAIN,
                    SwapProvider.ONEINCH,
                    SwapProvider.LIFI,
                )

                ticker in mayaEthTokens -> setOf(
                    SwapProvider.ONEINCH,
                    SwapProvider.LIFI,
                    SwapProvider.MAYA,
                )

                else -> setOf(SwapProvider.ONEINCH, SwapProvider.LIFI)
            }

            Chain.BscChain -> if (ticker in thorBscTokens) setOf(
                SwapProvider.THORCHAIN,
                SwapProvider.ONEINCH,
                SwapProvider.LIFI,
            ) else setOf(SwapProvider.ONEINCH, SwapProvider.LIFI)

            Chain.Avalanche -> if (ticker in thorAvaxTokens) setOf(
                SwapProvider.THORCHAIN,
                SwapProvider.ONEINCH,
                SwapProvider.LIFI,
            ) else setOf(SwapProvider.ONEINCH, SwapProvider.LIFI)

            Chain.Base -> setOf(SwapProvider.LIFI)

            Chain.Optimism, Chain.Polygon -> setOf(
                SwapProvider.ONEINCH, SwapProvider.LIFI
            )

            Chain.ThorChain -> setOf(
                SwapProvider.THORCHAIN,
                SwapProvider.MAYA,
            )

            Chain.Bitcoin -> setOf(
                SwapProvider.THORCHAIN,
                SwapProvider.MAYA,
            )

            Chain.Dogecoin, Chain.BitcoinCash, Chain.Litecoin,
            Chain.GaiaChain,
                -> setOf(
                SwapProvider.THORCHAIN
            )

            Chain.Arbitrum -> if (ticker in mayaArbTokens) setOf(
                SwapProvider.LIFI,
                SwapProvider.MAYA
            ) else setOf(SwapProvider.LIFI)

            Chain.Blast -> setOf(SwapProvider.LIFI)

            Chain.Solana, Chain.Polkadot, Chain.Dydx,
            Chain.CronosChain, Chain.ZkSync, Chain.Sui,
            Chain.Ton -> emptySet()
        }


    companion object {
        private const val FIXED_THOR_SWAP_DECIMALS = 8
        private const val FIXED_MAYA_SWAP_DECIMALS = 8
    }

}

private fun Chain.swapAssetName(): String {
    // TODO that seems to differ just for thorChain
    return when (this) {
        Chain.ThorChain -> "THOR"
        Chain.Ethereum -> "ETH"
        Chain.Avalanche -> "AVAX"
        Chain.BscChain -> "BSC"
        Chain.Bitcoin -> "BTC"
        Chain.BitcoinCash -> "BCH"
        Chain.Litecoin -> "LTC"
        Chain.Dogecoin -> "DOGE"
        Chain.GaiaChain -> "GAIA"
        Chain.Kujira -> "KUJI"
        Chain.Solana -> "SOL"
        Chain.Dash -> "DASH"
        Chain.MayaChain -> "MAYA"
        Chain.Arbitrum -> "ARB"
        Chain.Base -> "BASE"
        Chain.Optimism -> "OP"
        Chain.Polygon -> "POL"
        Chain.Blast -> "BLAST"
        Chain.CronosChain -> "CRO"
        Chain.Polkadot -> "DOT"
        Chain.Dydx -> "DYDX"
        Chain.ZkSync -> "ZK"
        Chain.Sui -> "SUI"
        Chain.Ton -> "TON"
    }
}
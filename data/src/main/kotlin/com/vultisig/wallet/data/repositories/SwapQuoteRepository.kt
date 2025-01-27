package com.vultisig.wallet.data.repositories

import com.vultisig.wallet.data.api.JupiterApi
import com.vultisig.wallet.data.api.LiFiChainApi
import com.vultisig.wallet.data.api.MayaChainApi
import com.vultisig.wallet.data.api.OneInchApi
import com.vultisig.wallet.data.api.ThorChainApi
import com.vultisig.wallet.data.api.errors.SwapException
import com.vultisig.wallet.data.api.models.LiFiSwapQuoteDeserialized
import com.vultisig.wallet.data.api.models.OneInchSwapQuoteDeserialized
import com.vultisig.wallet.data.api.models.OneInchSwapQuoteJson
import com.vultisig.wallet.data.api.models.OneInchSwapTxJson
import com.vultisig.wallet.data.api.models.THORChainSwapQuoteDeserialized
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.SwapProvider
import com.vultisig.wallet.data.models.SwapQuote
import com.vultisig.wallet.data.models.TokenValue
import com.vultisig.wallet.data.models.oneInchChainId
import com.vultisig.wallet.data.models.swapAssetName
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

    suspend fun getJupiterSwapQuote(
        srcAddress: String,
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
    private val jupiterApi: JupiterApi,
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
        when (oneInchQuote) {
            is OneInchSwapQuoteDeserialized.Error -> throw SwapException.handleSwapException(oneInchQuote.error)
            is OneInchSwapQuoteDeserialized.Result -> {
                oneInchQuote.data.error?.let { throw SwapException.handleSwapException(it) }
                return oneInchQuote.data
            }
        }
    }

    override suspend fun getMayaSwapQuote(
        dstAddress: String,
        srcToken: Coin,
        dstToken: Coin,
        tokenValue: TokenValue,
        isAffiliate: Boolean,
    ): SwapQuote {
        val thorTokenValue = (tokenValue.decimal * srcToken.thorswapMultiplier).toBigInteger()

        val mayaQuoteResult = mayaChainApi.getSwapQuotes(
            address = dstAddress,
            fromAsset = srcToken.swapAssetName(),
            toAsset = dstToken.swapAssetName(),
            amount = thorTokenValue.toString(),
            interval = srcToken.streamingInterval,
            isAffiliate = isAffiliate,
        )

        when (mayaQuoteResult) {
            is THORChainSwapQuoteDeserialized.Error ->
                throw SwapException.handleSwapException(mayaQuoteResult.error.message)

            is THORChainSwapQuoteDeserialized.Result -> {
                val mayaQuote = mayaQuoteResult.data

                mayaQuote.error?.let { throw SwapException.handleSwapException(it) }

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
        }
    }

    override suspend fun getSwapQuote(
        dstAddress: String,
        srcToken: Coin,
        dstToken: Coin,
        tokenValue: TokenValue,
        isAffiliate: Boolean,
    ): SwapQuote {
        val thorTokenValue = (tokenValue.decimal * srcToken.thorswapMultiplier).toBigInteger()

        val thorQuote = try {
            thorChainApi.getSwapQuotes(
                address = dstAddress,
                fromAsset = srcToken.swapAssetName(),
                toAsset = dstToken.swapAssetName(),
                amount = thorTokenValue.toString(),
                interval = "1",
                isAffiliate = isAffiliate,
            )
        } catch (e: Exception) {
            throw SwapException.handleSwapException(e.message ?: "Unknown error")
        }

        when (thorQuote) {
            is THORChainSwapQuoteDeserialized.Error -> {
                throw SwapException.handleSwapException(thorQuote.error.message)
            }

            is THORChainSwapQuoteDeserialized.Result -> {
                thorQuote.data.error?.let { throw SwapException.handleSwapException(it) }
                val tokenFees = thorQuote.data.fees.total
                    .convertToTokenValue(dstToken)

                val expectedDstTokenValue = thorQuote.data.expectedAmountOut
                    .convertToTokenValue(dstToken)

                val recommendedMinTokenValue = thorQuote.data.recommendedMinAmountIn.convertToTokenValue(srcToken)

                return SwapQuote.ThorChain(
                    expectedDstValue = expectedDstTokenValue,
                    recommendedMinTokenValue = recommendedMinTokenValue,
                    fees = tokenFees,
                    data = thorQuote.data,
                )
            }
        }



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

        val toToken = if (dstToken.ticker == "CRO") "0x0000000000000000000000000000000000000000"
        else dstToken.contractAddress.ifEmpty { dstToken.ticker }

        val liFiQuoteResponse = try {
            liFiChainApi.getSwapQuote(
                fromChain = srcToken.chain.oneInchChainId().toString(),
                toChain = dstToken.chain.oneInchChainId().toString(),
                fromToken = fromToken,
                toToken = toToken,
                fromAmount = tokenValue.value.toString(),
                fromAddress = srcAddress,
                toAddress = dstAddress,
            )
        } catch (e: Exception) {
            throw SwapException.handleSwapException(e.message ?: "Unknown error")
        }

        when (liFiQuoteResponse) {
            is LiFiSwapQuoteDeserialized.Error ->
                throw SwapException.handleSwapException(liFiQuoteResponse.error.message)

            is LiFiSwapQuoteDeserialized.Result -> {
                val liFiQuote = liFiQuoteResponse.data
                val swapFee = liFiQuote.estimate.feeCosts
                    .filterNot { it.included }
                    .sumOf { it.amount.toBigInteger() }
                    .toString()
                liFiQuote.message?.let { throw SwapException.handleSwapException(it) }
                return OneInchSwapQuoteJson(
                    dstAmount = liFiQuote.estimate.toAmount,
                    tx = OneInchSwapTxJson(
                        from = liFiQuote.transactionRequest.from ?: "",
                        to = liFiQuote.transactionRequest.to ?: "",
                        data = liFiQuote.transactionRequest.data,
                        gas = liFiQuote.transactionRequest.gasLimit?.substring(startIndex = 2)
                            ?.hexToLong() ?: 0,
                        value = liFiQuote.transactionRequest.value?.substring(startIndex = 2)
                            ?.hexToLong()
                            ?.toString() ?: "0",
                        gasPrice = liFiQuote.transactionRequest.gasPrice?.substring(startIndex = 2)
                            ?.hexToLong()?.toString() ?: "0",
                        swapFee = swapFee,
                    )
                )
            }
        }
    }

    override suspend fun getJupiterSwapQuote(
        srcAddress: String,
        srcToken: Coin,
        dstToken: Coin,
        tokenValue: TokenValue,
    ): OneInchSwapQuoteJson {

        val fromToken =
            srcToken.contractAddress.ifEmpty { SOLANA_DEFAULT_CONTRACT_ADDRESS }

        val toToken =
            dstToken.contractAddress.ifEmpty { SOLANA_DEFAULT_CONTRACT_ADDRESS }

        val jupiterQuote = try {
            jupiterApi.getSwapQuote(
                fromToken = fromToken,
                toToken = toToken,
                fromAmount = tokenValue.value.toString(),
                fromAddress = srcAddress,
            )
        } catch (e: Exception) {
            throw SwapException.handleSwapException(e.message ?: "Unknown error")
        }

        val swapFee = jupiterQuote.routePlan
            .sumOf { it.swapInfo.feeAmount.toLong() }.toString()
        return OneInchSwapQuoteJson(
            dstAmount = jupiterQuote.dstAmount,
            tx = OneInchSwapTxJson(
                from = "",
                to = "",
                data = jupiterQuote.swapTransaction.data,
                gas = 0,
                value = "0",
                gasPrice = "0",
                swapFee = swapFee,
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
        if (chain == Chain.Kujira && (contractAddress.contains("factory/") || contractAddress.contains("ibc/"))) {
            "${chain.swapAssetName()}.${ticker}"
        } else
            "${chain.swapAssetName()}.${ticker}-${contractAddress}"
    }

    override fun resolveProvider(srcToken: Coin, dstToken: Coin): SwapProvider? {
        if (hasNotProvider(srcToken, dstToken)) return null
        return srcToken.swapProviders
            .intersect(dstToken.swapProviders)
            .firstOrNull {
                if (isCrossChainSwap(srcToken, dstToken))
                    it != SwapProvider.ONEINCH
                else true
            }
    }

    private fun isCrossChainSwap(srcToken: Coin, dstToken: Coin) =
        srcToken.chain != dstToken.chain

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

            Chain.Base -> setOf(
                SwapProvider.LIFI,
                SwapProvider.THORCHAIN,
            )

            Chain.Optimism, Chain.Polygon, Chain.ZkSync -> setOf(
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

            Chain.Blast, Chain.CronosChain-> setOf(SwapProvider.LIFI)
            Chain.Solana -> setOf(SwapProvider.JUPITER, SwapProvider.LIFI)

            Chain.Polkadot, Chain.Dydx, Chain.Sui, Chain.Ton, Chain.Osmosis,
            Chain.Terra, Chain.TerraClassic, Chain.Noble, Chain.Ripple, Chain.Akash, Chain.Tron-> emptySet()
        }


    companion object {
        private const val SOLANA_DEFAULT_CONTRACT_ADDRESS =
            "So11111111111111111111111111111111111111112"
    }
}
package com.vultisig.wallet.data.repositories

import com.vultisig.wallet.data.api.JupiterApi
import com.vultisig.wallet.data.api.LiFiChainApi
import com.vultisig.wallet.data.api.MayaChainApi
import com.vultisig.wallet.data.api.ThorChainApi
import com.vultisig.wallet.data.api.errors.SwapException
import com.vultisig.wallet.data.api.models.KyberSwapRouteResponse
import com.vultisig.wallet.data.api.models.quotes.EVMSwapQuoteDeserialized
import com.vultisig.wallet.data.api.models.quotes.EVMSwapQuoteJson
import com.vultisig.wallet.data.api.models.quotes.KyberSwapQuoteDeserialized
import com.vultisig.wallet.data.api.models.quotes.KyberSwapQuoteJson
import com.vultisig.wallet.data.api.models.quotes.LiFiSwapQuoteDeserialized
import com.vultisig.wallet.data.api.models.quotes.OneInchSwapTxJson
import com.vultisig.wallet.data.api.models.quotes.THORChainSwapQuoteDeserialized
import com.vultisig.wallet.data.api.models.quotes.dstAmount
import com.vultisig.wallet.data.api.models.quotes.gasForChain
import com.vultisig.wallet.data.api.swapAggregators.KyberApi
import com.vultisig.wallet.data.api.swapAggregators.OneInchApi
import com.vultisig.wallet.data.chains.helpers.EvmHelper
import com.vultisig.wallet.data.common.convertToBigIntegerOrZero
import com.vultisig.wallet.data.common.isNotEmptyContract
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.SwapProvider
import com.vultisig.wallet.data.models.SwapQuote
import com.vultisig.wallet.data.models.SwapQuote.Companion.expiredAfter
import com.vultisig.wallet.data.models.TokenValue
import com.vultisig.wallet.data.models.oneInchChainId
import com.vultisig.wallet.data.models.swapAssetName
import kotlinx.datetime.Clock
import java.math.BigDecimal
import javax.inject.Inject

interface SwapQuoteRepository {

    suspend fun getSwapQuote(
        dstAddress: String,
        srcToken: Coin,
        dstToken: Coin,
        tokenValue: TokenValue,
        referralCode: String = "",
        bpsDiscount: Int = 0,
    ): SwapQuote

    suspend fun getKyberSwapQuote(
        srcToken: Coin,
        dstToken: Coin,
        tokenValue: TokenValue,
        isAffiliate: Boolean,
    ): EVMSwapQuoteJson

    suspend fun getOneInchSwapQuote(
        srcToken: Coin,
        dstToken: Coin,
        tokenValue: TokenValue,
        isAffiliate: Boolean,
        bpsDiscount: Int = 0,
    ): EVMSwapQuoteJson

    suspend fun getMayaSwapQuote(
        dstAddress: String,
        srcToken: Coin,
        dstToken: Coin,
        tokenValue: TokenValue,
        isAffiliate: Boolean,
        bpsDiscount: Int = 0,
    ): SwapQuote

    suspend fun getLiFiSwapQuote(
        srcAddress: String,
        dstAddress: String,
        srcToken: Coin,
        dstToken: Coin,
        tokenValue: TokenValue,
        bpsDiscount: Int,
    ): EVMSwapQuoteJson

    suspend fun getJupiterSwapQuote(
        srcAddress: String,
        srcToken: Coin,
        dstToken: Coin,
        tokenValue: TokenValue,
    ): EVMSwapQuoteJson

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
    private val kyberApi: KyberApi
) : SwapQuoteRepository {

    override suspend fun getOneInchSwapQuote(
        srcToken: Coin,
        dstToken: Coin,
        tokenValue: TokenValue,
        isAffiliate: Boolean,
        bpsDiscount: Int,
    ): EVMSwapQuoteJson {
        val oneInchQuote = oneInchApi.getSwapQuote(
            chain = srcToken.chain,
            srcTokenContractAddress = srcToken.contractAddress,
            dstTokenContractAddress = dstToken.contractAddress,
            srcAddress = srcToken.address,
            amount = tokenValue.value.toString(),
            isAffiliate = isAffiliate,
            bpsDiscount = bpsDiscount,
        )
        when (oneInchQuote) {
            is EVMSwapQuoteDeserialized.Error -> throw SwapException.handleSwapException(
                oneInchQuote.error
            )

            is EVMSwapQuoteDeserialized.Result -> {
                oneInchQuote.data.error?.let { throw SwapException.handleSwapException(it) }
                return oneInchQuote.data
            }
        }
    }

    override suspend fun getKyberSwapQuote(
        srcToken: Coin,
        dstToken: Coin,
        tokenValue: TokenValue,
        isAffiliate: Boolean,
    ): EVMSwapQuoteJson {
        val kyberSwapQuote = kyberApi.getSwapQuote(
            chain = srcToken.chain,
            srcTokenContractAddress = srcToken.contractAddress,
            dstTokenContractAddress = dstToken.contractAddress,
            amount = tokenValue.value.toString(),
            srcAddress = srcToken.address,
            isAffiliate = isAffiliate
        )
        when (kyberSwapQuote) {
            is KyberSwapQuoteDeserialized.Error ->
                throw SwapException.handleSwapException(kyberSwapQuote.error.message)

            is KyberSwapQuoteDeserialized.Result -> {

                return buildTransaction(
                    coin = srcToken,
                    routeSummary = kyberSwapQuote.result.data.routeSummary,
                    response = kyberApi.getKyberSwapQuote(
                        chain = srcToken.chain,
                        routeSummary = kyberSwapQuote.result.data.routeSummary,
                        from = srcToken.address,
                        enableGasEstimation = true,
                        isAffiliate = isAffiliate
                    ),
                )
            }
        }
    }


    private fun buildTransaction(
        coin: Coin,
        routeSummary: KyberSwapRouteResponse.RouteSummary,
        response: KyberSwapQuoteJson,
    ): EVMSwapQuoteJson {
        val gasPrice = routeSummary.gasPrice
        val calculatedGas = response.gasForChain(coin.chain)
        val finalGas =
            if (calculatedGas == 0L) EvmHelper.DEFAULT_ETH_SWAP_GAS_UNIT else calculatedGas

        return EVMSwapQuoteJson(
            dstAmount = response.dstAmount,
            tx = OneInchSwapTxJson(
                from = coin.address,
                to = response.data.routerAddress,
                gas = finalGas,
                data = response.data.data,
                value = response.data.transactionValue,
                gasPrice = gasPrice,
            )
        )
    }


    override suspend fun getMayaSwapQuote(
        dstAddress: String,
        srcToken: Coin,
        dstToken: Coin,
        tokenValue: TokenValue,
        isAffiliate: Boolean,
        bpsDiscount: Int,
    ): SwapQuote {
        val thorTokenValue = (tokenValue.decimal * srcToken.thorswapMultiplier).toBigInteger()

        val mayaQuoteResult = mayaChainApi.getSwapQuotes(
            address = dstAddress,
            fromAsset = srcToken.swapAssetName(),
            toAsset = dstToken.swapAssetName(),
            amount = thorTokenValue.toString(),
            isAffiliate = isAffiliate,
            bpsDiscount = bpsDiscount,
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
                    expiredAt = Clock.System.now() + expiredAfter
                )
            }
        }
    }

    override suspend fun getSwapQuote(
        dstAddress: String,
        srcToken: Coin,
        dstToken: Coin,
        tokenValue: TokenValue,
        referralCode: String,
        bpsDiscount: Int,
    ): SwapQuote {
        val thorTokenValue = (tokenValue.decimal * srcToken.thorswapMultiplier).toBigInteger()

        val thorQuote = try {
            thorChainApi.getSwapQuotes(
                address = dstAddress,
                fromAsset = srcToken.swapAssetName(),
                toAsset = dstToken.swapAssetName(),
                amount = thorTokenValue.toString(),
                interval = "1",
                referralCode = referralCode,
                bpsDiscount = bpsDiscount,
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

                val recommendedMinTokenValue =
                    thorQuote.data.recommendedMinAmountIn.convertToTokenValue(srcToken)

                return SwapQuote.ThorChain(
                    expectedDstValue = expectedDstTokenValue,
                    recommendedMinTokenValue = recommendedMinTokenValue,
                    fees = tokenFees,
                    data = thorQuote.data,
                    expiredAt = Clock.System.now() + expiredAfter,
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
        bpsDiscount: Int,
    ): EVMSwapQuoteJson {

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
                bpsDiscount = bpsDiscount,
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
                    // adapted from vultisig-windows:
                    // https://github.com/vultisig/vultisig-windows/blob/5cb9748bc88efa8b375132c93ba1906e1ccccebe/core/chain/swap/general/lifi/api/getLifiSwapQuote.ts#L70
                    .find {
                        it.name.equals(
                            "LIFI Fixed Fee",
                            ignoreCase = true
                        )
                    }

                val swapFeeToken = swapFee?.token?.address
                    ?.takeIf { it.isNotEmptyContract() }
                    ?: ""

                liFiQuote.message?.let { throw SwapException.handleSwapException(it) }
                return EVMSwapQuoteJson(
                    dstAmount = liFiQuote.estimate.toAmount,
                    tx = OneInchSwapTxJson(
                        from = liFiQuote.transactionRequest.from ?: "",
                        to = liFiQuote.transactionRequest.to ?: "",
                        data = liFiQuote.transactionRequest.data,
                        gas = liFiQuote.transactionRequest.gasLimit?.substring(startIndex = 2)
                            ?.hexToLong() ?: 0,
                        value = liFiQuote.transactionRequest.value?.substring(startIndex = 2)
                            ?.convertToBigIntegerOrZero().toString(),
                        gasPrice = liFiQuote.transactionRequest.gasPrice?.substring(startIndex = 2)
                            ?.hexToLong()?.toString() ?: "0",
                        swapFee = swapFee?.amount ?: "0",
                        swapFeeTokenContract = swapFeeToken,
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
    ): EVMSwapQuoteJson {

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
            .firstOrNull { it.swapInfo.feeMint == fromToken }?.swapInfo?.feeAmount ?: "0"

        val swapFeeTokenContract = jupiterQuote.routePlan.firstOrNull()?.swapInfo?.feeMint ?: ""

        return EVMSwapQuoteJson(
            dstAmount = jupiterQuote.dstAmount,
            tx = OneInchSwapTxJson(
                from = "",
                to = "",
                data = jupiterQuote.swapTransaction.data,
                gas = 0,
                value = "0",
                gasPrice = "0",
                swapFee = swapFee,
                swapFeeTokenContract = swapFeeTokenContract
            )
        )
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
            "${chain.swapAssetName()}.${ticker}"
        }
    } else {
        if (chain == Chain.Kujira && (contractAddress.contains("factory/") || contractAddress.contains(
                "ibc/"
            ))
        ) {
            "${chain.swapAssetName()}.${ticker}"
        } else if (chain == Chain.ThorChain)
            if (contractAddress.contains(Regex("""\w+-\w+""")))
                contractAddress else
                "${chain.swapAssetName()}.${ticker}"
        else
            "${chain.swapAssetName()}.${ticker}-${contractAddress}"
    }

    override fun resolveProvider(srcToken: Coin, dstToken: Coin): SwapProvider? {
        return srcToken.swapProviders
            .intersect(dstToken.swapProviders)
            .firstOrNull {
                if (isCrossChainSwap(
                        srcToken,
                        dstToken
                    )
                )
                    it != SwapProvider.ONEINCH && it != SwapProvider.KYBER
                else true
            }
    }

    private fun isCrossChainSwap(srcToken: Coin, dstToken: Coin) =
        srcToken.chain != dstToken.chain

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
        "LLD",
    )
    private val thorBscTokens = listOf(
        "BNB",
        "USDT",
        "USDC"
    )
    private val thorAvaxTokens = listOf(
        "AVAX",
        "USDC",
        "USDT",
        "SOL"
    )
    private val mayaEthTokens = listOf(
        "ETH",
        "USDC",
        "LLD",
    )
    private val mayaArbTokens = listOf(
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
        "USDC"
    )

    private val Coin.swapProviders: Set<SwapProvider>
        get() = when (chain) {
            Chain.MayaChain, Chain.Dash, Chain.Kujira -> setOf(SwapProvider.MAYA)
            Chain.Ethereum -> when {
                ticker.uppercase() in thorEthTokens && ticker.uppercase() in mayaEthTokens -> setOf(
                    SwapProvider.THORCHAIN,
                    SwapProvider.ONEINCH,
                    SwapProvider.LIFI,
                    SwapProvider.KYBER,
                    SwapProvider.MAYA,
                )

                ticker.uppercase() in thorEthTokens -> setOf(
                    SwapProvider.THORCHAIN,
                    SwapProvider.ONEINCH,
                    SwapProvider.LIFI,
                    SwapProvider.KYBER,
                )

                ticker.uppercase() in mayaEthTokens -> setOf(
                    SwapProvider.ONEINCH,
                    SwapProvider.LIFI,
                    SwapProvider.MAYA,
                    SwapProvider.KYBER,
                )

                else -> setOf(
                    SwapProvider.ONEINCH,
                    SwapProvider.LIFI,
                    SwapProvider.KYBER
                )
            }

            Chain.BscChain -> if (ticker in thorBscTokens) setOf(
                SwapProvider.THORCHAIN,
                SwapProvider.ONEINCH,
                SwapProvider.LIFI,
                SwapProvider.KYBER,
            ) else setOf(
                SwapProvider.ONEINCH,
                SwapProvider.LIFI,
                SwapProvider.KYBER
            )

            Chain.Avalanche -> if (ticker in thorAvaxTokens) setOf(
                SwapProvider.THORCHAIN,
                SwapProvider.ONEINCH,
                SwapProvider.LIFI,
                SwapProvider.KYBER,
            ) else setOf(
                SwapProvider.ONEINCH,
                SwapProvider.LIFI,
                SwapProvider.KYBER,
            )

            Chain.Base -> setOf(
                SwapProvider.LIFI,
                SwapProvider.THORCHAIN,
            )

            Chain.Optimism, Chain.Polygon, Chain.ZkSync -> setOf(
                SwapProvider.ONEINCH,
                SwapProvider.LIFI
            )
            Chain.Mantle -> setOf(
                SwapProvider.LIFI,
                SwapProvider.KYBER,
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

            Chain.Zcash -> setOf(SwapProvider.MAYA)

            Chain.Arbitrum -> if (ticker.uppercase() in mayaArbTokens) setOf(
                SwapProvider.LIFI,
                SwapProvider.MAYA
            ) else setOf(SwapProvider.LIFI)

            Chain.Blast, Chain.CronosChain -> setOf(SwapProvider.LIFI)
            Chain.Solana -> setOf(
                SwapProvider.JUPITER,
                SwapProvider.LIFI
            )

            Chain.Ripple -> setOf(SwapProvider.THORCHAIN)
            Chain.Tron -> setOf(SwapProvider.THORCHAIN)

            Chain.Hyperliquid -> setOf(SwapProvider.LIFI)

            Chain.Polkadot, Chain.Dydx, Chain.Sui, Chain.Ton, Chain.Osmosis,
            Chain.Terra, Chain.TerraClassic, Chain.Noble, Chain.Akash, Chain.Cardano, Chain.Sei,
                -> emptySet()
        }


    companion object {
        private const val SOLANA_DEFAULT_CONTRACT_ADDRESS =
            "So11111111111111111111111111111111111111112"
    }
}

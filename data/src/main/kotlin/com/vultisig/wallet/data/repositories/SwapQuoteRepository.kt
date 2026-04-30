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
import com.vultisig.wallet.data.api.models.quotes.THORChainSwapQuote
import com.vultisig.wallet.data.api.models.quotes.THORChainSwapQuoteDeserialized
import com.vultisig.wallet.data.api.models.quotes.ThorChainSwapQuoteRequest
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
import com.vultisig.wallet.data.models.swapAssetComparisonName
import com.vultisig.wallet.data.models.swapAssetName
import com.vultisig.wallet.data.utils.thorswapMultiplier
import java.math.BigDecimal
import java.math.BigInteger
import javax.inject.Inject
import kotlinx.datetime.Clock
import timber.log.Timber

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
        affiliateBps: Int,
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
        referralCode: String = "",
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

    fun resolveProvider(srcToken: Coin, dstToken: Coin): SwapProvider?

    fun getEligibleProviders(srcToken: Coin, dstToken: Coin): List<SwapProvider>
}

internal class SwapQuoteRepositoryImpl
@Inject
constructor(
    private val thorChainApi: ThorChainApi,
    private val mayaChainApi: MayaChainApi,
    private val oneInchApi: OneInchApi,
    private val liFiChainApi: LiFiChainApi,
    private val jupiterApi: JupiterApi,
    private val kyberApi: KyberApi,
) : SwapQuoteRepository {

    override suspend fun getOneInchSwapQuote(
        srcToken: Coin,
        dstToken: Coin,
        tokenValue: TokenValue,
        isAffiliate: Boolean,
        bpsDiscount: Int,
    ): EVMSwapQuoteJson {
        val oneInchQuote =
            oneInchApi.getSwapQuote(
                chain = srcToken.chain,
                srcTokenContractAddress = srcToken.contractAddress,
                dstTokenContractAddress = dstToken.contractAddress,
                srcAddress = srcToken.address,
                amount = tokenValue.value.toString(),
                isAffiliate = isAffiliate,
                bpsDiscount = bpsDiscount,
            )
        when (oneInchQuote) {
            is EVMSwapQuoteDeserialized.Error ->
                throw SwapException.handleSwapException(oneInchQuote.error)

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
        affiliateBps: Int,
    ): EVMSwapQuoteJson {
        val kyberSwapQuote =
            kyberApi.getSwapQuote(
                chain = srcToken.chain,
                srcTokenContractAddress = srcToken.contractAddress,
                dstTokenContractAddress = dstToken.contractAddress,
                amount = tokenValue.value.toString(),
                srcAddress = srcToken.address,
                affiliateBps = affiliateBps,
            )
        when (kyberSwapQuote) {
            is KyberSwapQuoteDeserialized.Error ->
                throw SwapException.handleSwapException(kyberSwapQuote.error.message)

            is KyberSwapQuoteDeserialized.Result -> {

                return buildTransaction(
                    coin = srcToken,
                    routeSummary = kyberSwapQuote.result.data.routeSummary,
                    response =
                        kyberApi.getKyberSwapQuote(
                            chain = srcToken.chain,
                            routeSummary = kyberSwapQuote.result.data.routeSummary,
                            from = srcToken.address,
                            enableGasEstimation = true,
                            affiliateBps = affiliateBps,
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
            tx =
                OneInchSwapTxJson(
                    from = coin.address,
                    to = response.data.routerAddress,
                    gas = finalGas,
                    data = response.data.data,
                    value = response.data.transactionValue,
                    gasPrice = gasPrice,
                    swapFee =
                        routeSummary.extraFee?.let { fee ->
                            if (fee.isInBps) {
                                (routeSummary.amountOut.toBigInteger() *
                                        fee.feeAmount.toBigInteger() / 10000.toBigInteger())
                                    .toString()
                            } else fee.feeAmount
                        } ?: "0",
                    swapFeeTokenContract = routeSummary.tokenOut,
                ),
        )
    }

    override suspend fun getMayaSwapQuote(
        dstAddress: String,
        srcToken: Coin,
        dstToken: Coin,
        tokenValue: TokenValue,
        isAffiliate: Boolean,
        bpsDiscount: Int,
        referralCode: String,
    ): SwapQuote {
        val thorTokenValue = (tokenValue.decimal * srcToken.thorswapMultiplier).toBigInteger()

        val mayaQuoteResult =
            mayaChainApi.getSwapQuotes(
                address = dstAddress,
                fromAsset = srcToken.swapAssetName(),
                toAsset = dstToken.swapAssetName(),
                amount = thorTokenValue.toString(),
                isAffiliate = isAffiliate,
                bpsDiscount = bpsDiscount,
                referralCode = referralCode,
            )

        when (mayaQuoteResult) {
            is THORChainSwapQuoteDeserialized.Error ->
                throw SwapException.handleSwapException(mayaQuoteResult.error.message)

            is THORChainSwapQuoteDeserialized.Result -> {
                val mayaQuote = mayaQuoteResult.data

                mayaQuote.error?.let { throw SwapException.handleSwapException(it) }

                val tokenFees = mayaQuote.fees.total.convertToTokenValue(dstToken)

                val expectedDstTokenValue =
                    mayaQuote.expectedAmountOut.convertToTokenValue(dstToken)

                val recommendedMinTokenValue =
                    if (srcToken.chain != Chain.MayaChain) {
                        mayaQuote.recommendedMinAmountIn.convertToTokenValue(srcToken)
                    } else tokenValue

                return SwapQuote.MayaChain(
                    expectedDstValue = expectedDstTokenValue,
                    fees = tokenFees,
                    data = mayaQuote,
                    recommendedMinTokenValue = recommendedMinTokenValue,
                    expiredAt = Clock.System.now() + expiredAfter,
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
        if (srcToken.swapAssetComparisonName() == dstToken.swapAssetComparisonName()) {
            throw SwapException.SameAssets("Source and Target cannot be the same")
        }
        val thorTokenValue = (tokenValue.decimal * srcToken.thorswapMultiplier).toBigInteger()

        val rapidRequest =
            ThorChainSwapQuoteRequest(
                address = dstAddress,
                fromAsset = srcToken.swapAssetName(),
                toAsset = dstToken.swapAssetName(),
                amount = thorTokenValue.toString(),
                interval = "0",
                referralCode = referralCode,
                bpsDiscount = bpsDiscount,
            )

        // Fetch rapid quote; capture failure so we can fall back to streaming
        var rapidData: THORChainSwapQuote? = null
        var rapidError: SwapException? = null
        try {
            when (val quote = thorChainApi.getSwapQuotes(rapidRequest)) {
                is THORChainSwapQuoteDeserialized.Error ->
                    rapidError = SwapException.handleSwapException(quote.error.message)

                is THORChainSwapQuoteDeserialized.Result ->
                    if (quote.data.error != null)
                        rapidError = SwapException.handleSwapException(quote.data.error)
                    else rapidData = quote.data
            }
        } catch (e: Exception) {
            rapidError = SwapException.handleSwapException(e.message ?: "Unknown error")
        }

        // Decide whether to try streaming: always when rapid failed, or when slippage is too high
        val streamingQuantityHint: Int?
        val needsStreaming: Boolean

        if (rapidData == null) {
            Timber.w("Rapid quote failed, trying streaming fallback")
            needsStreaming = true
            streamingQuantityHint = null
        } else {
            // local val so the compiler can smart-cast inside this block
            val rd = rapidData
            val feesTotal = rd.fees.total.toBigInteger()
            val rapidExpectedOut = rd.expectedAmountOut.toBigInteger()
            val grossOut = feesTotal + rapidExpectedOut
            val rapidSlippageBps =
                if (grossOut > BigInteger.ZERO)
                    feesTotal.multiply(BigInteger.valueOf(10000)).divide(grossOut).toInt()
                else 0
            needsStreaming = rapidSlippageBps > STREAMING_SLIPPAGE_THRESHOLD_BPS
            streamingQuantityHint = if (needsStreaming) rd.maxStreamingQuantity else null
            if (needsStreaming) {
                Timber.d(
                    "Slippage %d bps is above threshold, fetching streaming quote",
                    rapidSlippageBps,
                )
            }
        }

        // val copy so the compiler can smart-cast in the when expression below
        val rapidSnapshot: THORChainSwapQuote? = rapidData

        val finalData: THORChainSwapQuote =
            if (needsStreaming) {
                val streamingData: THORChainSwapQuote? =
                    try {
                        val quote =
                            thorChainApi.getSwapQuotes(
                                rapidRequest.copy(
                                    interval = "1",
                                    streamingQuantity = streamingQuantityHint,
                                )
                            )
                        when (quote) {
                            is THORChainSwapQuoteDeserialized.Error -> null
                            is THORChainSwapQuoteDeserialized.Result ->
                                quote.data.takeIf { it.error == null }
                        }
                    } catch (e: Exception) {
                        Timber.w(e, "Streaming quote fetch failed")
                        null
                    }

                pickBestQuote(
                    rapid = rapidSnapshot,
                    streaming = streamingData,
                    rapidError = rapidError,
                )
            } else {
                requireNotNull(rapidSnapshot)
            }

        val tokenFees = finalData.fees.total.convertToTokenValue(dstToken)
        val expectedDstTokenValue = finalData.expectedAmountOut.convertToTokenValue(dstToken)
        val recommendedMinTokenValue =
            finalData.recommendedMinAmountIn.convertToTokenValue(srcToken)

        return SwapQuote.ThorChain(
            expectedDstValue = expectedDstTokenValue,
            recommendedMinTokenValue = recommendedMinTokenValue,
            fees = tokenFees,
            data = finalData,
            expiredAt = Clock.System.now() + expiredAfter,
        )
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

        val fromToken = srcToken.contractAddress.ifEmpty { srcToken.ticker }

        val toToken =
            if (dstToken.ticker == "CRO") "0x0000000000000000000000000000000000000000"
            else dstToken.contractAddress.ifEmpty { dstToken.ticker }

        val liFiQuoteResponse =
            try {
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
                val swapFee =
                    liFiQuote.estimate.feeCosts
                        // adapted from vultisig-windows:
                        // https://github.com/vultisig/vultisig-windows/blob/5cb9748bc88efa8b375132c93ba1906e1ccccebe/core/chain/swap/general/lifi/api/getLifiSwapQuote.ts#L70
                        .find { it.name.equals("LIFI Fixed Fee", ignoreCase = true) }

                val swapFeeToken = swapFee?.token?.address?.takeIf { it.isNotEmptyContract() } ?: ""

                liFiQuote.message?.let { throw SwapException.handleSwapException(it) }
                return EVMSwapQuoteJson(
                    dstAmount = liFiQuote.estimate.toAmount,
                    tx =
                        OneInchSwapTxJson(
                            from = liFiQuote.transactionRequest.from ?: "",
                            to = liFiQuote.transactionRequest.to ?: "",
                            data = liFiQuote.transactionRequest.data,
                            gas =
                                liFiQuote.transactionRequest.gasLimit
                                    .convertToBigIntegerOrZero()
                                    .toLong(),
                            value =
                                liFiQuote.transactionRequest.value
                                    .convertToBigIntegerOrZero()
                                    .toString(),
                            gasPrice =
                                liFiQuote.transactionRequest.gasPrice
                                    .convertToBigIntegerOrZero()
                                    .toString(),
                            swapFee = swapFee?.amount ?: "0",
                            swapFeeTokenContract = swapFeeToken,
                        ),
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

        val fromToken = srcToken.contractAddress.ifEmpty { SOLANA_DEFAULT_CONTRACT_ADDRESS }

        val toToken = dstToken.contractAddress.ifEmpty { SOLANA_DEFAULT_CONTRACT_ADDRESS }

        val jupiterQuote =
            try {
                jupiterApi.getSwapQuote(
                    fromToken = fromToken,
                    toToken = toToken,
                    fromAmount = tokenValue.value.toString(),
                    fromAddress = srcAddress,
                )
            } catch (e: Exception) {
                throw SwapException.handleSwapException(e.message ?: "Unknown error")
            }

        val swapFee =
            jupiterQuote.routePlan
                .firstOrNull { it.swapInfo.feeMint == fromToken }
                ?.swapInfo
                ?.feeAmount ?: "0"

        val swapFeeTokenContract = jupiterQuote.routePlan.firstOrNull()?.swapInfo?.feeMint ?: ""

        return EVMSwapQuoteJson(
            dstAmount = jupiterQuote.dstAmount,
            tx =
                OneInchSwapTxJson(
                    from = "",
                    to = "",
                    data = jupiterQuote.swapTransaction.data,
                    gas = 0,
                    value = "0",
                    gasPrice = "0",
                    swapFee = swapFee,
                    swapFeeTokenContract = swapFeeTokenContract,
                ),
        )
    }

    private fun pickBestQuote(
        rapid: THORChainSwapQuote?,
        streaming: THORChainSwapQuote?,
        rapidError: SwapException?,
    ): THORChainSwapQuote {
        if (streaming == null && rapid == null) throw requireNotNull(rapidError)
        if (streaming == null) return requireNotNull(rapid)
        if (rapid == null) return streaming
        val streamingOut = streaming.expectedAmountOut.toBigInteger()
        return if (streamingOut > rapid.expectedAmountOut.toBigInteger()) streaming else rapid
    }

    private fun String.convertToTokenValue(token: Coin): TokenValue =
        BigDecimal(this).divide(token.thorswapMultiplier).let {
            TokenValue(value = (it.movePointRight(token.decimal)).toBigInteger(), token = token)
        }

    override fun resolveProvider(srcToken: Coin, dstToken: Coin): SwapProvider? =
        getEligibleProviders(srcToken, dstToken).firstOrNull()

    override fun getEligibleProviders(srcToken: Coin, dstToken: Coin): List<SwapProvider> =
        srcToken.swapProviders.intersect(dstToken.swapProviders).filter {
            if (isCrossChainSwap(srcToken, dstToken))
                it != SwapProvider.ONEINCH && it != SwapProvider.KYBER
            else true
        }

    private fun isCrossChainSwap(srcToken: Coin, dstToken: Coin) = srcToken.chain != dstToken.chain

    private val thorEthTokens =
        listOf(
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
    private val thorBscTokens = listOf("BNB", "USDT", "USDC")
    private val thorAvaxTokens = listOf("AVAX", "USDC", "USDT", "SOL")
    private val thorBaseTokens = listOf("ETH", "CBBTC", "USDC", "VVV")
    private val mayaEthTokens = listOf("ETH", "USDC", "LLD")
    private val mayaArbTokens =
        listOf(
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

    private val Coin.swapProviders: Set<SwapProvider>
        get() =
            when (chain) {
                Chain.MayaChain,
                Chain.Dash,
                Chain.Kujira -> setOf(SwapProvider.MAYA)
                Chain.Ethereum ->
                    when {
                        ticker.uppercase() in thorEthTokens &&
                            ticker.uppercase() in mayaEthTokens ->
                            setOf(
                                SwapProvider.THORCHAIN,
                                SwapProvider.ONEINCH,
                                SwapProvider.LIFI,
                                SwapProvider.KYBER,
                                SwapProvider.MAYA,
                            )

                        ticker.uppercase() in thorEthTokens ->
                            setOf(
                                SwapProvider.THORCHAIN,
                                SwapProvider.ONEINCH,
                                SwapProvider.LIFI,
                                SwapProvider.KYBER,
                            )

                        ticker.uppercase() in mayaEthTokens ->
                            setOf(
                                SwapProvider.ONEINCH,
                                SwapProvider.LIFI,
                                SwapProvider.MAYA,
                                SwapProvider.KYBER,
                            )

                        else -> setOf(SwapProvider.ONEINCH, SwapProvider.LIFI, SwapProvider.KYBER)
                    }

                Chain.BscChain ->
                    if (ticker.uppercase() in thorBscTokens)
                        setOf(
                            SwapProvider.THORCHAIN,
                            SwapProvider.ONEINCH,
                            SwapProvider.LIFI,
                            SwapProvider.KYBER,
                        )
                    else setOf(SwapProvider.ONEINCH, SwapProvider.LIFI, SwapProvider.KYBER)

                Chain.Avalanche ->
                    if (ticker.uppercase() in thorAvaxTokens)
                        setOf(
                            SwapProvider.THORCHAIN,
                            SwapProvider.ONEINCH,
                            SwapProvider.LIFI,
                            SwapProvider.KYBER,
                        )
                    else setOf(SwapProvider.ONEINCH, SwapProvider.LIFI, SwapProvider.KYBER)

                Chain.Base ->
                    if (ticker.uppercase() in thorBaseTokens)
                        setOf(SwapProvider.LIFI, SwapProvider.THORCHAIN)
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
                    if (ticker.uppercase() in mayaArbTokens)
                        setOf(SwapProvider.LIFI, SwapProvider.MAYA)
                    else setOf(SwapProvider.LIFI)

                Chain.Blast,
                Chain.CronosChain -> setOf(SwapProvider.LIFI)
                Chain.Solana ->
                    if (isNativeToken)
                        setOf(SwapProvider.THORCHAIN, SwapProvider.JUPITER, SwapProvider.LIFI)
                    else setOf(SwapProvider.JUPITER, SwapProvider.LIFI)

                Chain.Ripple -> setOf(SwapProvider.THORCHAIN)
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

    companion object {
        private const val SOLANA_DEFAULT_CONTRACT_ADDRESS =
            "So11111111111111111111111111111111111111112"
        const val STREAMING_SLIPPAGE_THRESHOLD_BPS = 300
    }
}

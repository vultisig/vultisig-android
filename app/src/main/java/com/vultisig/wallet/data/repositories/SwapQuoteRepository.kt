package com.vultisig.wallet.data.repositories

import com.vultisig.wallet.data.api.LiFiChainApi
import com.vultisig.wallet.data.api.MayaChainApi
import com.vultisig.wallet.data.api.OneInchApi
import com.vultisig.wallet.data.api.ThorChainApi
import com.vultisig.wallet.data.api.errors.SwapException
import com.vultisig.wallet.data.api.models.OneInchSwapQuoteJson
import com.vultisig.wallet.data.api.models.OneInchSwapTxJson
import com.vultisig.wallet.data.models.SwapProvider
import com.vultisig.wallet.data.models.SwapQuote
import com.vultisig.wallet.data.models.TokenValue
import com.vultisig.wallet.models.Chain
import com.vultisig.wallet.models.Coin
import com.vultisig.wallet.models.oneInchChainId
import java.math.BigDecimal
import javax.inject.Inject
import kotlin.math.abs

internal interface SwapQuoteRepository {

    suspend fun getSwapQuote(
        dstAddress: String,
        srcToken: Coin,
        dstToken: Coin,
        tokenValue: TokenValue,
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
        tokenValue: TokenValue
    ): SwapQuote {
        val mayaQuote = mayaChainApi.getSwapQuotes(
            address = dstAddress,
            fromAsset = srcToken.swapAssetName(),
            toAsset = dstToken.swapAssetName(),
            amount = tokenValue.value.toString(),
            interval = "5"
        )

        SwapException.handleSwapException(mayaQuote.error)

        val tokenFees = TokenValue(mayaQuote.fees.total.toBigInteger(), dstToken)

        val expectedDstTokenValue = TokenValue(mayaQuote.expectedAmountOut.toBigInteger(), dstToken)

        return SwapQuote.MayaChain(
            expectedDstValue = expectedDstTokenValue,
            fees = tokenFees,
            data = mayaQuote,
        )
    }

    override suspend fun getSwapQuote(
        dstAddress: String,
        srcToken: Coin,
        dstToken: Coin,
        tokenValue: TokenValue,
    ): SwapQuote {
        val thorTokenValue = tokenValue.decimal
            .movePointRight(FIXED_THORSWAP_DECIMALS)
            .toBigInteger()

        val thorQuote = thorChainApi.getSwapQuotes(
            address = dstAddress,
            fromAsset = srcToken.swapAssetName(),
            toAsset = dstToken.swapAssetName(),
            amount = thorTokenValue.toString(),
            interval = "1"
        )

        SwapException.handleSwapException(thorQuote.error)

        val tokenFees = thorQuote.fees.total
            .thorTokenValueToTokenValue(dstToken, FIXED_THORSWAP_DECIMALS)

        val expectedDstTokenValue = thorQuote.expectedAmountOut
            .thorTokenValueToTokenValue(dstToken, FIXED_THORSWAP_DECIMALS)

        return SwapQuote.ThorChain(
            expectedDstValue = expectedDstTokenValue,
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

        val liFiQuote = liFiChainApi.getSwapQuote(
            fromChain = srcToken.chain.oneInchChainId().toString(),
            toChain = dstToken.chain.oneInchChainId().toString(),
            fromToken = srcToken.ticker,
            toToken = dstToken.ticker,
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
                value = liFiQuote.transactionRequest.value.substring(startIndex = 2).hexToLong().toString(),
                gasPrice = liFiQuote.transactionRequest.gasPrice.substring(startIndex = 2).hexToLong().toString(),
            )
        )
    }

    private fun String.thorTokenValueToTokenValue(
        token: Coin,
        decimals: Int,
    ): TokenValue {
        // convert maya token values with 10 decimal places to token values
        // with the correct number of decimal places
        val exponent = token.decimal - decimals
        val multiplier = if (exponent >= 0) {
            BigDecimal.TEN
        } else {
            BigDecimal(0.1)
        }.pow(abs(exponent))

        return TokenValue(
            value = this.toBigDecimal()
                .multiply(multiplier)
                .toBigInteger(),
            token = token,
        )
    }

    private fun Coin.swapAssetName(): String = if (isNativeToken) {
        if (chain == Chain.gaiaChain) {
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
        return srcToken.swapProviders.intersect(dstToken.swapProviders).firstOrNull()
    }

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
        "WSTETH",
        "TGT",
        "AAVE",
        "FOX",
        "DPI",
        "SNX"
    )
    private val thorBscTokens = listOf("BNB", "USDT", "USDC")
    private val thorAvaxTokens = listOf("AVAX", "USDC", "USDT", "SOL")

    private val Coin.swapProviders: Set<SwapProvider>
        get() = when (chain) {
            Chain.mayaChain, Chain.dash, Chain.kujira -> setOf(SwapProvider.MAYA)
            Chain.ethereum -> if (ticker in thorEthTokens) setOf(
                SwapProvider.THORCHAIN,
                SwapProvider.ONEINCH,
                SwapProvider.LIFI,
            ) else setOf(SwapProvider.ONEINCH, SwapProvider.LIFI)

            Chain.bscChain -> if (ticker in thorBscTokens) setOf(
                SwapProvider.THORCHAIN,
                SwapProvider.ONEINCH,
                SwapProvider.LIFI,
            ) else setOf(SwapProvider.ONEINCH, SwapProvider.LIFI)

            Chain.avalanche -> if (ticker in thorAvaxTokens) setOf(
                SwapProvider.THORCHAIN,
                SwapProvider.ONEINCH,
                SwapProvider.LIFI,
            ) else setOf(SwapProvider.ONEINCH, SwapProvider.LIFI)

            Chain.base -> setOf(SwapProvider.LIFI)

            Chain.optimism, Chain.polygon -> setOf(
                SwapProvider.ONEINCH, SwapProvider.LIFI
            )
            Chain.thorChain -> setOf(
                SwapProvider.THORCHAIN,
                SwapProvider.MAYA,
            )
            Chain.bitcoin -> setOf(
                SwapProvider.THORCHAIN,
                SwapProvider.MAYA,
            )
            Chain.dogecoin, Chain.bitcoinCash, Chain.litecoin,
            Chain.gaiaChain -> setOf(
                SwapProvider.THORCHAIN
            )
            Chain.arbitrum, Chain.blast -> setOf(SwapProvider.LIFI)

            Chain.solana, Chain.polkadot, Chain.dydx,
            Chain.cronosChain, /* TODO later Chain.sui, Chain.zksync*/
            -> emptySet()
        }


    companion object {

        private const val FIXED_THORSWAP_DECIMALS = 8
    }

}

private fun Chain.swapAssetName(): String {
    // TODO that seems to differ just for thorChain
    return when (this) {
        Chain.thorChain -> "THOR"
        Chain.ethereum -> "ETH"
        Chain.avalanche -> "AVAX"
        Chain.bscChain -> "BSC"
        Chain.bitcoin -> "BTC"
        Chain.bitcoinCash -> "BCH"
        Chain.litecoin -> "LTC"
        Chain.dogecoin -> "DOGE"
        Chain.gaiaChain -> "GAIA"
        Chain.kujira -> "KUJI"
        Chain.solana -> "SOL"
        Chain.dash -> "DASH"
        Chain.mayaChain -> "MAYA"
        Chain.arbitrum -> "ARB"
        Chain.base -> "BASE"
        Chain.optimism -> "OP"
        Chain.polygon -> "MATIC"
        Chain.blast -> "BLAST"
        Chain.cronosChain -> "CRO"
        Chain.polkadot -> "DOT"
        Chain.dydx -> "DYDX"
//        Chain.sui -> "SUI"
//        Chain.zksync -> "ZK"
    }
}
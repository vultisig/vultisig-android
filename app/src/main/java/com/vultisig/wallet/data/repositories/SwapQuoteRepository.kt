package com.vultisig.wallet.data.repositories

import com.vultisig.wallet.data.api.MayaChainApi
import com.vultisig.wallet.data.api.OneInchApi
import com.vultisig.wallet.data.api.ThorChainApi
import com.vultisig.wallet.data.api.errors.SwapException
import com.vultisig.wallet.data.api.models.OneInchSwapQuoteJson
import com.vultisig.wallet.data.models.SwapProvider
import com.vultisig.wallet.data.models.SwapQuote
import com.vultisig.wallet.data.models.TokenValue
import com.vultisig.wallet.models.Chain
import com.vultisig.wallet.models.Coin
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

    fun resolveProvider(
        srcToken: Coin,
        dstToken: Coin,
    ): SwapProvider?

}

internal class SwapQuoteRepositoryImpl @Inject constructor(
    private val thorChainApi: ThorChainApi,
    private val mayaChainApi: MayaChainApi,
    private val oneInchApi: OneInchApi,
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

        SwapException.handleSwapError(oneInchQuote.error)
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

        SwapException.handleSwapError(mayaQuote.error)

        val tokenFees = mayaQuote.fees.total
            .mayaTokenValueToTokenValue(dstToken)

        val expectedDstTokenValue = mayaQuote.expectedAmountOut
            .mayaTokenValueToTokenValue(dstToken)

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

        SwapException.handleSwapError(thorQuote.error)

        val tokenFees = thorQuote.fees.total
            .thorTokenValueToTokenValue(dstToken)

        val expectedDstTokenValue = thorQuote.expectedAmountOut
            .thorTokenValueToTokenValue(dstToken)

        return SwapQuote.ThorChain(
            expectedDstValue = expectedDstTokenValue,
            fees = tokenFees,
            data = thorQuote,
        )
    }

    private fun String.thorTokenValueToTokenValue(
        token: Coin,
    ): TokenValue {
        // convert thor token values with 8 decimal places to token values
        // with the correct number of decimal places
        val exponent = token.decimal - 8
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

    private fun String.mayaTokenValueToTokenValue(
        token: Coin,
    ): TokenValue {
        // convert maya token values with 10 decimal places to token values
        // with the correct number of decimal places
        val exponent = token.decimal - 10
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
                SwapProvider.ONEINCH
            ) else setOf(SwapProvider.ONEINCH)

            Chain.bscChain -> if (ticker in thorBscTokens) setOf(
                SwapProvider.THORCHAIN,
                SwapProvider.ONEINCH
            ) else setOf(SwapProvider.ONEINCH)

            Chain.avalanche -> if (ticker in thorAvaxTokens) setOf(
                SwapProvider.THORCHAIN,
                SwapProvider.ONEINCH
            ) else setOf(SwapProvider.ONEINCH)

            Chain.base, Chain.optimism, Chain.polygon -> setOf(SwapProvider.ONEINCH)
            Chain.thorChain, Chain.bitcoin, Chain.dogecoin, Chain.bitcoinCash, Chain.litecoin,
            Chain.gaiaChain -> setOf(
                SwapProvider.THORCHAIN
            )

            Chain.solana, Chain.polkadot, Chain.dydx, Chain.arbitrum, Chain.blast,
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
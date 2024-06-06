package com.vultisig.wallet.data.repositories

import com.vultisig.wallet.data.api.ThorChainApi
import com.vultisig.wallet.data.models.SwapQuote
import com.vultisig.wallet.data.models.TokenValue
import com.vultisig.wallet.models.Chain
import com.vultisig.wallet.models.Coin
import java.math.BigDecimal
import java.math.BigInteger
import javax.inject.Inject
import kotlin.math.abs
import kotlin.time.DurationUnit
import kotlin.time.toDuration

internal interface SwapQuoteRepository {

    suspend fun getSwapQuote(
        dstAddress: String,
        srcToken: Coin,
        dstToken: Coin,
        tokenValue: BigInteger,
    ): SwapQuote

}

internal class SwapQuoteRepositoryImpl @Inject constructor(
    private val thorChainApi: ThorChainApi,
) : SwapQuoteRepository {

    override suspend fun getSwapQuote(
        dstAddress: String,
        srcToken: Coin,
        dstToken: Coin,
        tokenValue: BigInteger,
    ): SwapQuote {
        val thorQuote = thorChainApi.getSwapQuotes(
            address = dstAddress,
            fromAsset = srcToken.swapAssetName(),
            toAsset = dstToken.swapAssetName(),
            amount = tokenValue.toString(),
            interval = "1"
        )

        val tokenFees = thorQuote.fees.total
            .thorTokenValueToTokenValue(dstToken)

        val estimatedTime = thorQuote.totalSwapSeconds
            ?.toDuration(DurationUnit.SECONDS)

        val expectedDstTokenValue = thorQuote.expectedAmountOut
            .thorTokenValueToTokenValue(dstToken)

        return SwapQuote(
            expectedDstValue = expectedDstTokenValue,
            fees = tokenFees,
            estimatedTime = estimatedTime,
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

        return this.toBigDecimal()
            .multiply(multiplier)
            .toBigInteger()
            .let {
                TokenValue(
                    it,
                    token.ticker,
                    token.decimal,
                )
            }
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
        Chain.mayaChain -> "CACAO"
        Chain.arbitrum -> "ARB"
        Chain.base -> "BASE"
        Chain.optimism -> "OP"
        Chain.polygon -> "MATIC"
        Chain.blast -> "BLAST"
        Chain.cronosChain -> "CRO"
        Chain.polkadot -> "DOT"

//        Chain.sui -> "SUI"
//        Chain.zksync -> "ZK"
    }
}
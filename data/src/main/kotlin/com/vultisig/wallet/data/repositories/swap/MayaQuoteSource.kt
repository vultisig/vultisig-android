package com.vultisig.wallet.data.repositories.swap

import com.vultisig.wallet.data.api.MayaChainApi
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.SwapQuote
import com.vultisig.wallet.data.models.SwapQuote.Companion.expiredAfter
import com.vultisig.wallet.data.models.TokenValue
import com.vultisig.wallet.data.models.swapAssetName
import javax.inject.Inject
import kotlinx.datetime.Clock

data class MayaQuoteRequest(
    val dstAddress: String,
    val srcToken: Coin,
    val dstToken: Coin,
    val tokenValue: TokenValue,
    val isAffiliate: Boolean,
    val bpsDiscount: Int,
    val referralCode: String,
)

interface MayaQuoteSource {
    suspend fun fetch(request: MayaQuoteRequest): SwapQuote
}

internal class MayaQuoteSourceImpl @Inject constructor(private val mayaChainApi: MayaChainApi) :
    MayaQuoteSource {

    override suspend fun fetch(request: MayaQuoteRequest): SwapQuote {
        val response =
            mayaChainApi.getSwapQuotes(
                address = request.dstAddress,
                fromAsset = request.srcToken.swapAssetName(),
                toAsset = request.dstToken.swapAssetName(),
                amount = request.srcToken.toThorTokenValue(request.tokenValue).toString(),
                isAffiliate = request.isAffiliate,
                bpsDiscount = request.bpsDiscount,
                referralCode = request.referralCode,
            )
        val data = response.unwrapOrThrow()
        val recommendedMin =
            if (request.srcToken.chain != Chain.MayaChain) {
                data.recommendedMinAmountIn.convertToTokenValue(request.srcToken)
            } else request.tokenValue

        return SwapQuote.MayaChain(
            expectedDstValue = data.expectedAmountOut.convertToTokenValue(request.dstToken),
            fees = data.fees.total.convertToTokenValue(request.dstToken),
            recommendedMinTokenValue = recommendedMin,
            data = data,
            expiredAt = Clock.System.now() + expiredAfter,
        )
    }
}

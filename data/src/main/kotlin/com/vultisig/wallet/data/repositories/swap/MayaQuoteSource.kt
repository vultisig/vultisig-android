package com.vultisig.wallet.data.repositories.swap

import com.vultisig.wallet.data.api.MayaChainApi
import com.vultisig.wallet.data.api.errors.SwapException
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.SwapQuote
import com.vultisig.wallet.data.models.SwapQuote.Companion.expiredAfter
import com.vultisig.wallet.data.models.swapAssetComparisonName
import com.vultisig.wallet.data.models.swapAssetName
import javax.inject.Inject
import kotlinx.datetime.Clock

internal class MayaQuoteSource @Inject constructor(private val mayaChainApi: MayaChainApi) :
    SwapQuoteSource {

    override suspend fun fetch(request: SwapQuoteRequest): SwapQuoteResult {
        val srcToken = request.srcToken
        val dstToken = request.dstToken
        if (srcToken.swapAssetComparisonName() == dstToken.swapAssetComparisonName()) {
            throw SwapException.SameAssets("Source and Target cannot be the same")
        }

        require(request.dstAddress.isNotBlank()) { "dstAddress is required for Maya swap quotes" }

        val response =
            swapApiCall("Maya") {
                mayaChainApi.getSwapQuotes(
                    address = request.dstAddress,
                    fromAsset = srcToken.swapAssetName(),
                    toAsset = dstToken.swapAssetName(),
                    amount = srcToken.toThorTokenValue(request.tokenValue).toString(),
                    isAffiliate = request.isAffiliate,
                    bpsDiscount = request.bpsDiscount,
                    referralCode = request.referralCode,
                )
            }
        val data = response.unwrapOrThrow()
        val recommendedMin =
            if (srcToken.chain != Chain.MayaChain) {
                srcToken.convertToTokenValue(data.recommendedMinAmountIn)
            } else request.tokenValue

        return SwapQuoteResult.Native(
            SwapQuote.MayaChain(
                expectedDstValue = dstToken.convertToTokenValue(data.expectedAmountOut),
                fees = dstToken.convertToTokenValue(data.fees.total),
                recommendedMinTokenValue = recommendedMin,
                data = data,
                expiredAt = Clock.System.now() + expiredAfter,
            )
        )
    }
}

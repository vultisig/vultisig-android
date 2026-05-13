package com.vultisig.wallet.data.repositories.swap

import com.vultisig.wallet.data.api.errors.SwapException
import com.vultisig.wallet.data.api.models.quotes.EVMSwapQuoteDeserialized
import com.vultisig.wallet.data.api.swapAggregators.OneInchApi
import javax.inject.Inject

internal class OneInchQuoteSource @Inject constructor(private val oneInchApi: OneInchApi) :
    SwapQuoteSource {

    override suspend fun fetch(request: SwapQuoteRequest): SwapQuoteResult {
        val response =
            swapApiCall("OneInch") {
                oneInchApi.getSwapQuote(
                    chain = request.srcToken.chain,
                    srcTokenContractAddress = request.srcToken.contractAddress,
                    dstTokenContractAddress = request.dstToken.contractAddress,
                    srcAddress = request.srcToken.address,
                    amount = request.tokenValue.value.toString(),
                    isAffiliate = request.isAffiliate,
                    bpsDiscount = request.bpsDiscount,
                )
            }
        return when (response) {
            is EVMSwapQuoteDeserialized.Error ->
                throw SwapException.handleSwapException(response.error)

            is EVMSwapQuoteDeserialized.Result -> {
                response.data.error?.let { throw SwapException.handleSwapException(it) }
                SwapQuoteResult.Evm(response.data)
            }
        }
    }
}

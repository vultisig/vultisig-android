package com.vultisig.wallet.data.repositories.swap

import com.vultisig.wallet.data.api.errors.SwapException
import com.vultisig.wallet.data.api.models.quotes.EVMSwapQuoteDeserialized
import com.vultisig.wallet.data.api.models.quotes.EVMSwapQuoteJson
import com.vultisig.wallet.data.api.swapAggregators.OneInchApi
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.TokenValue
import javax.inject.Inject

data class OneInchQuoteRequest(
    val srcToken: Coin,
    val dstToken: Coin,
    val tokenValue: TokenValue,
    val isAffiliate: Boolean,
    val bpsDiscount: Int,
)

interface OneInchQuoteSource {
    suspend fun fetch(request: OneInchQuoteRequest): EVMSwapQuoteJson
}

internal class OneInchQuoteSourceImpl @Inject constructor(private val oneInchApi: OneInchApi) :
    OneInchQuoteSource {

    override suspend fun fetch(request: OneInchQuoteRequest): EVMSwapQuoteJson {
        val response =
            oneInchApi.getSwapQuote(
                chain = request.srcToken.chain,
                srcTokenContractAddress = request.srcToken.contractAddress,
                dstTokenContractAddress = request.dstToken.contractAddress,
                srcAddress = request.srcToken.address,
                amount = request.tokenValue.value.toString(),
                isAffiliate = request.isAffiliate,
                bpsDiscount = request.bpsDiscount,
            )
        return when (response) {
            is EVMSwapQuoteDeserialized.Error ->
                throw SwapException.handleSwapException(response.error)

            is EVMSwapQuoteDeserialized.Result -> {
                response.data.error?.let { throw SwapException.handleSwapException(it) }
                response.data
            }
        }
    }
}

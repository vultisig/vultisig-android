package com.vultisig.wallet.data.repositories.swap

import com.vultisig.wallet.data.api.LiFiChainApi
import com.vultisig.wallet.data.api.errors.SwapException
import com.vultisig.wallet.data.api.models.quotes.EVMSwapQuoteJson
import com.vultisig.wallet.data.api.models.quotes.LiFiSwapQuoteDeserialized
import com.vultisig.wallet.data.api.models.quotes.LiFiSwapQuoteJson
import com.vultisig.wallet.data.api.models.quotes.OneInchSwapTxJson
import com.vultisig.wallet.data.common.ZERO_ADDRESS
import com.vultisig.wallet.data.common.convertToBigIntegerOrZero
import com.vultisig.wallet.data.common.isNotEmptyContract
import com.vultisig.wallet.data.models.oneInchChainId
import javax.inject.Inject

internal class LiFiQuoteSource @Inject constructor(private val liFiChainApi: LiFiChainApi) :
    SwapQuoteSource {

    override suspend fun fetch(request: SwapQuoteRequest): SwapQuoteResult {
        val srcToken = request.srcToken
        val dstToken = request.dstToken
        val fromToken = srcToken.contractAddress.ifEmpty { srcToken.ticker }
        val toToken =
            if (dstToken.ticker == "CRO") ZERO_ADDRESS
            else dstToken.contractAddress.ifEmpty { dstToken.ticker }

        val response =
            swapApiCall("LiFi") {
                liFiChainApi.getSwapQuote(
                    fromChain = srcToken.chain.oneInchChainId().toString(),
                    toChain = dstToken.chain.oneInchChainId().toString(),
                    fromToken = fromToken,
                    toToken = toToken,
                    fromAmount = request.tokenValue.value.toString(),
                    fromAddress = request.srcAddress,
                    toAddress = request.dstAddress,
                    bpsDiscount = request.bpsDiscount,
                )
            }

        return when (response) {
            is LiFiSwapQuoteDeserialized.Error ->
                throw SwapException.handleSwapException(response.error.message)

            is LiFiSwapQuoteDeserialized.Result -> SwapQuoteResult.Evm(response.data.toEvmQuote())
        }
    }

    private fun LiFiSwapQuoteJson.toEvmQuote(): EVMSwapQuoteJson {
        message?.let { throw SwapException.handleSwapException(it) }
        // adapted from vultisig-windows:
        // https://github.com/vultisig/vultisig-windows/blob/5cb9748bc88efa8b375132c93ba1906e1ccccebe/core/chain/swap/general/lifi/api/getLifiSwapQuote.ts#L70
        val swapFee =
            estimate.feeCosts.find { it.name.equals(LIFI_FIXED_FEE_NAME, ignoreCase = true) }
        val swapFeeToken = swapFee?.token?.address?.takeIf { it.isNotEmptyContract() } ?: ""

        return EVMSwapQuoteJson(
            dstAmount = estimate.toAmount,
            tx =
                OneInchSwapTxJson(
                    from = transactionRequest.from ?: "",
                    to = transactionRequest.to ?: "",
                    data = transactionRequest.data,
                    gas = transactionRequest.gasLimit.convertToBigIntegerOrZero().toLong(),
                    value = transactionRequest.value.convertToBigIntegerOrZero().toString(),
                    gasPrice = transactionRequest.gasPrice.convertToBigIntegerOrZero().toString(),
                    swapFee = swapFee?.amount ?: "0",
                    swapFeeTokenContract = swapFeeToken,
                ),
        )
    }

    private companion object {
        const val LIFI_FIXED_FEE_NAME = "LIFI Fixed Fee"
    }
}

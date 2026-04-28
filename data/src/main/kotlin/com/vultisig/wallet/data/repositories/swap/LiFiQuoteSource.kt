package com.vultisig.wallet.data.repositories.swap

import com.vultisig.wallet.data.api.LiFiChainApi
import com.vultisig.wallet.data.api.errors.SwapException
import com.vultisig.wallet.data.api.models.quotes.EVMSwapQuoteJson
import com.vultisig.wallet.data.api.models.quotes.LiFiSwapQuoteDeserialized
import com.vultisig.wallet.data.api.models.quotes.OneInchSwapTxJson
import com.vultisig.wallet.data.common.convertToBigIntegerOrZero
import com.vultisig.wallet.data.common.isNotEmptyContract
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.TokenValue
import com.vultisig.wallet.data.models.oneInchChainId
import javax.inject.Inject

data class LiFiQuoteRequest(
    val srcAddress: String,
    val dstAddress: String,
    val srcToken: Coin,
    val dstToken: Coin,
    val tokenValue: TokenValue,
    val bpsDiscount: Int,
)

interface LiFiQuoteSource {
    suspend fun fetch(request: LiFiQuoteRequest): EVMSwapQuoteJson
}

internal class LiFiQuoteSourceImpl @Inject constructor(private val liFiChainApi: LiFiChainApi) :
    LiFiQuoteSource {

    @OptIn(ExperimentalStdlibApi::class)
    override suspend fun fetch(request: LiFiQuoteRequest): EVMSwapQuoteJson {
        val srcToken = request.srcToken
        val dstToken = request.dstToken
        val fromToken = srcToken.contractAddress.ifEmpty { srcToken.ticker }
        val toToken =
            if (dstToken.ticker == "CRO") CRO_NATIVE_PLACEHOLDER
            else dstToken.contractAddress.ifEmpty { dstToken.ticker }

        val response =
            try {
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
            } catch (e: Exception) {
                throw SwapException.handleSwapException(e.message ?: "Unknown error")
            }

        return when (response) {
            is LiFiSwapQuoteDeserialized.Error ->
                throw SwapException.handleSwapException(response.error.message)

            is LiFiSwapQuoteDeserialized.Result -> {
                val data = response.data
                data.message?.let { throw SwapException.handleSwapException(it) }
                // adapted from vultisig-windows:
                // https://github.com/vultisig/vultisig-windows/blob/5cb9748bc88efa8b375132c93ba1906e1ccccebe/core/chain/swap/general/lifi/api/getLifiSwapQuote.ts#L70
                val swapFee =
                    data.estimate.feeCosts.find {
                        it.name.equals("LIFI Fixed Fee", ignoreCase = true)
                    }
                val swapFeeToken = swapFee?.token?.address?.takeIf { it.isNotEmptyContract() } ?: ""

                EVMSwapQuoteJson(
                    dstAmount = data.estimate.toAmount,
                    tx =
                        OneInchSwapTxJson(
                            from = data.transactionRequest.from ?: "",
                            to = data.transactionRequest.to ?: "",
                            data = data.transactionRequest.data,
                            gas =
                                data.transactionRequest.gasLimit
                                    .convertToBigIntegerOrZero()
                                    .toLong(),
                            value =
                                data.transactionRequest.value
                                    .convertToBigIntegerOrZero()
                                    .toString(),
                            gasPrice =
                                data.transactionRequest.gasPrice
                                    .convertToBigIntegerOrZero()
                                    .toString(),
                            swapFee = swapFee?.amount ?: "0",
                            swapFeeTokenContract = swapFeeToken,
                        ),
                )
            }
        }
    }

    private companion object {
        const val CRO_NATIVE_PLACEHOLDER = "0x0000000000000000000000000000000000000000"
    }
}

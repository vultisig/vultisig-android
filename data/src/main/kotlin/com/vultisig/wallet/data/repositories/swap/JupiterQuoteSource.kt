package com.vultisig.wallet.data.repositories.swap

import com.vultisig.wallet.data.api.JupiterApi
import com.vultisig.wallet.data.api.errors.SwapException
import com.vultisig.wallet.data.api.models.quotes.EVMSwapQuoteJson
import com.vultisig.wallet.data.api.models.quotes.OneInchSwapTxJson
import com.vultisig.wallet.data.chains.helpers.SOLANA_DEFAULT_CONTRACT_ADDRESS
import javax.inject.Inject

internal class JupiterQuoteSource @Inject constructor(private val jupiterApi: JupiterApi) :
    SwapQuoteSource {

    override suspend fun fetch(request: SwapQuoteRequest): SwapQuoteResult {
        val fromToken = request.srcToken.contractAddress.ifEmpty { SOLANA_DEFAULT_CONTRACT_ADDRESS }
        val toToken = request.dstToken.contractAddress.ifEmpty { SOLANA_DEFAULT_CONTRACT_ADDRESS }

        val quote =
            swapApiCall("Jupiter") {
                jupiterApi.getSwapQuote(
                    fromToken = fromToken,
                    toToken = toToken,
                    fromAmount = request.tokenValue.value.toString(),
                    fromAddress = request.srcAddress,
                )
            }

        val firstRoute =
            quote.routePlan.firstOrNull()
                ?: throw SwapException.handleSwapException("No swap route available")
        val swapFee =
            quote.routePlan.firstOrNull { it.swapInfo.feeMint == fromToken }?.swapInfo?.feeAmount
                ?: "0"

        return SwapQuoteResult.Evm(
            EVMSwapQuoteJson(
                dstAmount = quote.dstAmount,
                tx =
                    OneInchSwapTxJson(
                        from = "",
                        to = "",
                        data = quote.swapTransaction.data,
                        gas = 0,
                        value = "0",
                        gasPrice = "0",
                        swapFee = swapFee,
                        swapFeeTokenContract = firstRoute.swapInfo.feeMint.orEmpty(),
                    ),
            )
        )
    }
}

package com.vultisig.wallet.data.repositories.swap

import com.vultisig.wallet.data.api.JupiterApi
import com.vultisig.wallet.data.api.errors.SwapException
import com.vultisig.wallet.data.api.models.quotes.EVMSwapQuoteJson
import com.vultisig.wallet.data.api.models.quotes.OneInchSwapTxJson
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.TokenValue
import javax.inject.Inject

data class JupiterQuoteRequest(
    val srcAddress: String,
    val srcToken: Coin,
    val dstToken: Coin,
    val tokenValue: TokenValue,
)

interface JupiterQuoteSource {
    suspend fun fetch(request: JupiterQuoteRequest): EVMSwapQuoteJson
}

internal class JupiterQuoteSourceImpl @Inject constructor(private val jupiterApi: JupiterApi) :
    JupiterQuoteSource {

    override suspend fun fetch(request: JupiterQuoteRequest): EVMSwapQuoteJson {
        val fromToken = request.srcToken.contractAddress.ifEmpty { SOLANA_DEFAULT_CONTRACT_ADDRESS }
        val toToken = request.dstToken.contractAddress.ifEmpty { SOLANA_DEFAULT_CONTRACT_ADDRESS }

        val quote =
            try {
                jupiterApi.getSwapQuote(
                    fromToken = fromToken,
                    toToken = toToken,
                    fromAmount = request.tokenValue.value.toString(),
                    fromAddress = request.srcAddress,
                )
            } catch (e: Exception) {
                throw SwapException.handleSwapException(e.message ?: "Unknown error")
            }

        val swapFee =
            quote.routePlan.firstOrNull { it.swapInfo.feeMint == fromToken }?.swapInfo?.feeAmount
                ?: "0"
        val swapFeeTokenContract = quote.routePlan.firstOrNull()?.swapInfo?.feeMint ?: ""

        return EVMSwapQuoteJson(
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
                    swapFeeTokenContract = swapFeeTokenContract,
                ),
        )
    }

    private companion object {
        const val SOLANA_DEFAULT_CONTRACT_ADDRESS = "So11111111111111111111111111111111111111112"
    }
}

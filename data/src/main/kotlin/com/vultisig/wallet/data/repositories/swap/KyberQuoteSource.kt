package com.vultisig.wallet.data.repositories.swap

import com.vultisig.wallet.data.api.errors.SwapException
import com.vultisig.wallet.data.api.models.KyberSwapRouteResponse
import com.vultisig.wallet.data.api.models.quotes.EVMSwapQuoteJson
import com.vultisig.wallet.data.api.models.quotes.KyberSwapQuoteDeserialized
import com.vultisig.wallet.data.api.models.quotes.KyberSwapQuoteJson
import com.vultisig.wallet.data.api.models.quotes.OneInchSwapTxJson
import com.vultisig.wallet.data.api.models.quotes.dstAmount
import com.vultisig.wallet.data.api.models.quotes.gasForChain
import com.vultisig.wallet.data.api.swapAggregators.KyberApi
import com.vultisig.wallet.data.chains.helpers.EvmHelper
import com.vultisig.wallet.data.models.Coin
import javax.inject.Inject

internal class KyberQuoteSource @Inject constructor(private val kyberApi: KyberApi) :
    SwapQuoteSource {

    override suspend fun fetch(request: SwapQuoteRequest): SwapQuoteResult =
        swapApiCall("Kyber") {
            val routeResponse =
                kyberApi.getSwapQuote(
                    chain = request.srcToken.chain,
                    srcTokenContractAddress = request.srcToken.contractAddress,
                    dstTokenContractAddress = request.dstToken.contractAddress,
                    amount = request.tokenValue.value.toString(),
                    srcAddress = request.srcToken.address,
                    affiliateBps = request.affiliateBps,
                )
            when (routeResponse) {
                is KyberSwapQuoteDeserialized.Error ->
                    throw SwapException.handleSwapException(routeResponse.error.message)

                is KyberSwapQuoteDeserialized.Result -> {
                    val routeSummary = routeResponse.result.data.routeSummary
                    val txResponse =
                        kyberApi.getKyberSwapQuote(
                            chain = request.srcToken.chain,
                            routeSummary = routeSummary,
                            from = request.srcToken.address,
                            enableGasEstimation = true,
                            affiliateBps = request.affiliateBps,
                        )
                    SwapQuoteResult.Evm(
                        buildTransaction(
                            coin = request.srcToken,
                            routeSummary = routeSummary,
                            response = txResponse,
                        )
                    )
                }
            }
        }

    private fun buildTransaction(
        coin: Coin,
        routeSummary: KyberSwapRouteResponse.RouteSummary,
        response: KyberSwapQuoteJson,
    ): EVMSwapQuoteJson {
        val calculatedGas = response.gasForChain(coin.chain)
        val finalGas =
            if (calculatedGas == 0L) EvmHelper.DEFAULT_ETH_SWAP_GAS_UNIT else calculatedGas

        return EVMSwapQuoteJson(
            dstAmount = response.dstAmount,
            tx =
                OneInchSwapTxJson(
                    from = coin.address,
                    to = response.data.routerAddress,
                    gas = finalGas,
                    data = response.data.data,
                    value = response.data.transactionValue,
                    gasPrice = routeSummary.gasPrice,
                    swapFee = routeSummary.swapFeeAmount(),
                    swapFeeTokenContract = routeSummary.tokenOut,
                ),
        )
    }

    private fun KyberSwapRouteResponse.RouteSummary.swapFeeAmount(): String {
        val fee = extraFee ?: return "0"
        return if (fee.isInBps) {
            val absoluteFee = amountOut.toBigInteger() * fee.feeAmount.toBigInteger() / BPS_DIVISOR
            absoluteFee.toString()
        } else fee.feeAmount
    }
}

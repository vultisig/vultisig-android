package com.vultisig.wallet.ui.models.mappers

import com.vultisig.wallet.data.mappers.MapperFunc
import com.vultisig.wallet.data.models.SwapTransactionHistoryData
import com.vultisig.wallet.ui.models.swap.SwapTransactionUiModel
import javax.inject.Inject

internal interface SwapTransactionToHistoryDataMapper :
    MapperFunc<SwapTransactionUiModel, SwapTransactionHistoryData>

internal class SwapTransactionToHistoryDataMapperImpl @Inject constructor() :
    SwapTransactionToHistoryDataMapper {
    override fun invoke(from: SwapTransactionUiModel) =
        SwapTransactionHistoryData(
            fromToken = from.src.token.ticker,
            fromAmount = from.src.value,
            fromChain = from.src.token.chain.id,
            fromTokenLogo = from.src.token.logo,
            toToken = from.dst.token.ticker,
            toAmount = from.dst.value,
            toChain = from.dst.token.chain.id,
            toTokenLogo = from.dst.token.logo,
            provider = from.provider,
            fiatValue = from.src.fiatValue,
            swapId = from.swapId,
            toContractAddress = from.dst.token.contractAddress,
            toIsNative = from.dst.token.isNativeToken,
            fromAddress = from.src.token.address,
            toAmountDecimal = from.expectedDstDecimal,
            toDecimals = from.dst.token.decimal,
        )
}

package com.vultisig.wallet.ui.models.mappers

import com.vultisig.wallet.data.mappers.SuspendMapperFunc
import com.vultisig.wallet.data.models.SwapTransactionHistoryData
import com.vultisig.wallet.ui.models.swap.SwapTransactionUiModel
import javax.inject.Inject



internal interface SwapTransactionToHistoryDataMapper :
    SuspendMapperFunc<SwapTransactionUiModel, SwapTransactionHistoryData>

internal class SwapTransactionToHistoryDataMapperImpl @Inject constructor() :
    SwapTransactionToHistoryDataMapper {
    override suspend fun invoke(from: SwapTransactionUiModel) =
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
        )

}
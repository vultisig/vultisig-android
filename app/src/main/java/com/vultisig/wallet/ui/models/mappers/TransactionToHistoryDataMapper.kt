package com.vultisig.wallet.ui.models.mappers

import com.vultisig.wallet.data.mappers.MapperFunc
import com.vultisig.wallet.data.models.SendTransactionHistoryData
import com.vultisig.wallet.ui.models.TransactionDetailsUiModel
import javax.inject.Inject



internal interface SendTransactionHistoryDataMapper :
    MapperFunc<TransactionDetailsUiModel, SendTransactionHistoryData>

internal class SendTransactionHistoryDataMapperImpl @Inject constructor(
) : SendTransactionHistoryDataMapper {

    override fun invoke(from: TransactionDetailsUiModel) = SendTransactionHistoryData(
        fromAddress = from.srcAddress,
        toAddress = from.dstAddress,
        amount = from.token.value,
        token = from.token.token.ticker,
        tokenLogo = from.token.token.logo,
        feeEstimate = from.networkFeeTokenValue,
        memo = from.memo.orEmpty(),
    )
}




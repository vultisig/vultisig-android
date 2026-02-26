package com.vultisig.wallet.ui.models.mappers

import com.vultisig.wallet.data.mappers.MapperFunc
import com.vultisig.wallet.data.models.SendTransactionHistoryData
import com.vultisig.wallet.ui.models.deposit.DepositTransactionUiModel
import javax.inject.Inject

internal interface DepositTransactionHistoryDataMapper :
    MapperFunc<DepositTransactionUiModel, SendTransactionHistoryData>

internal class DepositTransactionHistoryDataMapperImpl @Inject constructor(
) : DepositTransactionHistoryDataMapper {

    override fun invoke(from: DepositTransactionUiModel) = SendTransactionHistoryData(
        fromAddress = from.srcAddress,
        toAddress = from.dstAddress,
        amount = from.token.value,
        token = from.token.token.ticker,
        tokenLogo = from.token.token.logo,
        feeEstimate = from.networkFeeTokenValue,
        memo = from.memo,
    )
}

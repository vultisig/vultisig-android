package com.vultisig.wallet.ui.screens.transaction

import com.vultisig.wallet.ui.models.SendTxUiModel
import com.vultisig.wallet.ui.models.deposit.DepositTransactionUiModel
import com.vultisig.wallet.ui.models.swap.ValuedToken

internal fun SendTxUiModel.toUiTransactionInfo(): UiTransactionInfo {
    return UiTransactionInfo(
        type = UiTransactionInfoType.Transfer,
        token = this.token,
        from = this.srcAddress,
        to = this.dstAddress,
        memo = this.memo ?: "",
        networkFeeFiatValue = this.networkFeeFiatValue,
        networkFeeTokenValue = this.networkFeeTokenValue,
    )
}

internal fun DepositTransactionUiModel.toUiTransactionInfo(): UiTransactionInfo {
    return UiTransactionInfo(
        type = UiTransactionInfoType.Deposit,
        token = ValuedToken.Empty,
        from = this.fromAddress,
        to = this.nodeAddress,
        memo = this.memo,
        networkFeeFiatValue = this.estimatedFees,
        networkFeeTokenValue = this.estimateFeesFiat,
    )
}
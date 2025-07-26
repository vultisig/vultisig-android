package com.vultisig.wallet.ui.screens.transaction

import com.vultisig.wallet.ui.models.keysign.TransactionTypeUiModel
import com.vultisig.wallet.ui.models.swap.ValuedToken

internal fun TransactionTypeUiModel.toUiTransactionInfo(): UiTransactionInfo {
    return when (this) {
        is TransactionTypeUiModel.Send -> {
            UiTransactionInfo(
                type = UiTransactionInfoType.Transfer,
                token = this.tx.token,
                from = this.tx.srcAddress,
                to = this.tx.dstAddress,
                memo = this.tx.memo ?: "",
                networkFeeFiatValue = this.tx.networkFeeFiatValue,
                networkFeeTokenValue = this.tx.networkFeeTokenValue,
            )
        }
        is TransactionTypeUiModel.Deposit -> {
            UiTransactionInfo(
                type = UiTransactionInfoType.Deposit,
                token = ValuedToken.Empty,
                from = this.depositTransactionUiModel.fromAddress,
                to = this.depositTransactionUiModel.nodeAddress,
                memo = this.depositTransactionUiModel.memo,
                networkFeeFiatValue = this.depositTransactionUiModel.estimatedFees,
                networkFeeTokenValue = this.depositTransactionUiModel.estimateFeesFiat,
            )
        }
        else -> error("Not supported $this")
    }
}
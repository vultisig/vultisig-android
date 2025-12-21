package com.vultisig.wallet.ui.screens.transaction

import com.vultisig.wallet.ui.models.keysign.TransactionTypeUiModel
import com.vultisig.wallet.ui.models.swap.ValuedToken

internal fun TransactionTypeUiModel.toUiTransactionInfo(): UiTransactionInfo {
    return when (this) {
        is TransactionTypeUiModel.Send -> {
            UiTransactionInfo(
                type = UiTransactionInfoType.Send,
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
                token = this.depositTransactionUiModel.token,
                from = this.depositTransactionUiModel.srcAddress,
                to = this.depositTransactionUiModel.dstAddress,
                memo = this.depositTransactionUiModel.memo,
                networkFeeFiatValue = this.depositTransactionUiModel.networkFeeFiatValue,
                networkFeeTokenValue = this.depositTransactionUiModel.networkFeeTokenValue,
            )
        }
        is TransactionTypeUiModel.SignMessage -> {
            UiTransactionInfo(
                type = UiTransactionInfoType.SignMessage,
                token = ValuedToken.Empty,
                from = "",
                to = "",
                memo = this.model.message,
                signMethod = this.model.method,
                networkFeeFiatValue = "",
                networkFeeTokenValue = "",
            )
        }
        else -> error("Not supported $this")
    }
}
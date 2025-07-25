package com.vultisig.wallet.ui.screens.transaction

import com.vultisig.wallet.ui.models.SendTxUiModel

internal fun SendTxUiModel.toUiTransactionInfo(): UiTransactionInfo {
    return UiTransactionInfo(
        token = this.token,
        from = this.srcAddress,
        to = this.dstAddress,
        memo = this.memo ?: "",
        networkFeeFiatValue = this.networkFeeFiatValue,
        networkFeeTokenValue = this.networkFeeTokenValue,
    )
}
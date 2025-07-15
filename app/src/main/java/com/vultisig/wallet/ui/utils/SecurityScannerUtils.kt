package com.vultisig.wallet.ui.utils

import com.vultisig.wallet.ui.models.TransactionScanStatus

fun handleSigningFlowCommon(
    txScanStatus: TransactionScanStatus,
    showWarning: () -> Unit,
    onSign: () -> Unit,
) {
    when (txScanStatus) {
        is TransactionScanStatus.Scanned -> {
            if (!txScanStatus.result.isSecure) {
                showWarning()
            } else {
                onSign()
            }
        }
        is TransactionScanStatus.Error,
        TransactionScanStatus.NotStarted,
        TransactionScanStatus.Scanning -> onSign()
    }
}
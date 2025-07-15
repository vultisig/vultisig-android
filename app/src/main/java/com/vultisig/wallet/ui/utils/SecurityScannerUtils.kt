package com.vultisig.wallet.ui.utils

import com.vultisig.wallet.ui.models.TransactionScanStatus

fun handleSigningFlowCommon(
    txScanStatus: TransactionScanStatus,
    showWarning: () -> Unit,
    onSign: () -> Unit,
    onSignAndSkipWarnings: () -> Unit
) {
    when (txScanStatus) {
        is TransactionScanStatus.Scanned -> {
            if (!txScanStatus.result.isSecure) {
                showWarning()
            } else {
                onSignAndSkipWarnings()
            }
        }
        is TransactionScanStatus.Error,
        TransactionScanStatus.NotStarted,
        TransactionScanStatus.Scanning -> onSign()
    }
}

/*
    private fun handleSigningFlow(
        onSign: () -> Unit,
        onSignAndSkipWarnings: () -> Unit
    ) {
        when (val status = uiState.value.txScanStatus) {
            is TransactionScanStatus.Scanned -> {
                if (!status.result.isSecure) {
                    uiState.update { it.copy(showScanningWarning = true) }
                } else {
                    onSignAndSkipWarnings()
                }
            }
            is TransactionScanStatus.Error,
            TransactionScanStatus.NotStarted,
            TransactionScanStatus.Scanning -> onSign()
        }
    }
 */
package com.vultisig.wallet.ui.screens.keysign

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.KeepScreenOn
import com.vultisig.wallet.ui.components.loader.VsSigningProgressIndicator
import com.vultisig.wallet.ui.models.TransactionUiModel
import com.vultisig.wallet.ui.models.keysign.KeysignState
import com.vultisig.wallet.ui.models.keysign.TransactionTypeUiModel
import com.vultisig.wallet.ui.screens.TransactionDoneView
import com.vultisig.wallet.ui.screens.transaction.SwapTransactionOverviewScreen

@Composable
internal fun KeysignView(
    state: KeysignState,
    txHash: String,
    approveTransactionHash: String,
    transactionLink: String,
    approveTransactionLink: String,
    onComplete: () -> Unit,
    onBack: () -> Unit = {},
    progressLink: String?,
    transactionTypeUiModel: TransactionTypeUiModel?,
    showToolbar: Boolean = false,
) {
    val text = when (state) {
        is KeysignState.CreatingInstance -> stringResource(id = R.string.keysign_screen_preparing_vault)
        is KeysignState.KeysignECDSA -> stringResource(id = R.string.keysign_screen_signing_with_ecdsa)
        is KeysignState.KeysignEdDSA -> stringResource(id = R.string.keysign_screen_signing_with_eddsa)
        is KeysignState.KeysignFinished -> stringResource(id = R.string.keysign_screen_keysign_finished)
        is KeysignState.Error -> stringResource(
            id = R.string.keysign_screen_error_please_try_again,
            state.errorMessage,
        )
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        when (state) {
            KeysignState.KeysignFinished -> {
                when (transactionTypeUiModel) {
                    is TransactionTypeUiModel.Swap -> {
                        SwapTransactionOverviewScreen(
                            showToolbar = showToolbar,
                            transactionHash = txHash,
                            approveTransactionHash = approveTransactionHash,
                            transactionLink = transactionLink,
                            approveTransactionLink = approveTransactionLink,
                            onComplete = onComplete,
                            progressLink = progressLink,
                            onBack = onBack,
                            transactionTypeUiModel = transactionTypeUiModel.swapTransactionUiModel,
                        )
                    }
                    else -> {
                        TransactionDoneView(
                            transactionHash = txHash,
                            approveTransactionHash = approveTransactionHash,
                            transactionLink = transactionLink,
                            approveTransactionLink = approveTransactionLink,
                            onComplete = onComplete,
                            progressLink = progressLink,
                            onBack = onBack,
                            transactionTypeUiModel = transactionTypeUiModel,
                            showToolbar = showToolbar,
                        )
                    }
                }
            }

            is KeysignState.Error -> {
                KeysignErrorScreen(
                    errorMessage = state.errorMessage,
                    tryAgain = onBack
                )
            }

            else -> {
                KeepScreenOn()

                VsSigningProgressIndicator(
                    text = text,
                )
            }
        }
    }
}

@Preview
@Composable
private fun KeysignPreview() {
    KeysignView(
        state = KeysignState.CreatingInstance,
        progressLink = null,
        txHash = "0x1234567890",
        approveTransactionHash = "0x1234567890",
        transactionLink = "",
        approveTransactionLink = "",
        transactionTypeUiModel = TransactionTypeUiModel.Send(
            TransactionUiModel(
                srcAddress = "0x1234567890",
                dstAddress = "0x1234567890",
                tokenValue = "1.1",
                fiatValue = "1.1",
                fiatCurrency = "USD",
                gasFeeValue = "1.1",
                memo = "some memo",
                estimatedFee = "0.75 USd",
                totalGas = "0.00031361"
            )
        ),
        onComplete = {},
    )
}

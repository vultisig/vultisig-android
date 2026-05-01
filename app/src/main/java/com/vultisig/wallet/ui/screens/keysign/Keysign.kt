package com.vultisig.wallet.ui.screens.keysign

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import app.rive.Fit
import app.rive.ViewModelSource
import app.rive.rememberViewModelInstance
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.KeepScreenOn
import com.vultisig.wallet.ui.components.loader.VsSigningProgressIndicator
import com.vultisig.wallet.ui.components.rive.RiveAnimation
import com.vultisig.wallet.ui.components.rive.rememberRiveResourceFile
import com.vultisig.wallet.ui.models.TransactionDetailsUiModel
import com.vultisig.wallet.ui.models.keysign.KeysignState
import com.vultisig.wallet.ui.models.keysign.TransactionStatus
import com.vultisig.wallet.ui.models.keysign.TransactionTypeUiModel
import com.vultisig.wallet.ui.models.keysign.progress
import com.vultisig.wallet.ui.screens.TransactionDoneView
import com.vultisig.wallet.ui.screens.transaction.SendTxOverviewScreen
import com.vultisig.wallet.ui.screens.transaction.SwapTransactionOverviewScreen
import com.vultisig.wallet.ui.screens.transaction.toUiTransactionInfo
import com.vultisig.wallet.ui.utils.VsUriHandler

private const val RIVE_PROGRESS_PROPERTY = "progessPercentage" // typo in riv_keysign.riv

@Composable
internal fun KeysignView(
    state: KeysignState,
    txHash: String,
    approveTransactionHash: String,
    transactionLink: String,
    approveTransactionLink: String,
    onComplete: () -> Unit,
    onAddToAddressBook: () -> Unit,
    onBack: () -> Unit = {},
    progressLink: String?,
    transactionTypeUiModel: TransactionTypeUiModel?,
    showToolbar: Boolean = false,
    hasBackClick: Boolean,
    showSaveToAddressBook: Boolean,
) {
    Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        when (state) {
            is KeysignState.KeysignFinished -> {
                when (transactionTypeUiModel) {
                    is TransactionTypeUiModel.Swap -> {
                        SwapTransactionOverviewScreen(
                            showToolbar = showToolbar,
                            transactionHash = txHash,
                            approveTransactionHash = approveTransactionHash,
                            transactionLink = transactionLink,
                            transactionStatus = state.transactionStatus,
                            approveTransactionLink = approveTransactionLink,
                            onComplete = onComplete,
                            progressLink = progressLink,
                            onBack = onBack,
                            transactionTypeUiModel = transactionTypeUiModel.swapTransactionUiModel,
                        )
                    }
                    is TransactionTypeUiModel.Deposit,
                    is TransactionTypeUiModel.Send -> {
                        SendTxOverviewScreen(
                            transactionHash = txHash,
                            transactionLink = transactionLink,
                            onComplete = onComplete,
                            onBack = onBack,
                            transactionStatus = state.transactionStatus,
                            tx = transactionTypeUiModel.toUiTransactionInfo(),
                            showToolbar = showToolbar,
                            onAddToAddressBook = onAddToAddressBook,
                            showSaveToAddressBook = showSaveToAddressBook,
                        )
                    }
                    else -> {

                        val uriHandler = VsUriHandler()
                        TransactionDoneView(
                            transactionHash = txHash,
                            approveTransactionHash = approveTransactionHash,
                            transactionLink = transactionLink,
                            approveTransactionLink = approveTransactionLink,
                            onComplete = onComplete,
                            onBack = onBack,
                            transactionTypeUiModel = transactionTypeUiModel,
                            showToolbar = showToolbar,
                            onUriClick = uriHandler::openUri,
                        )
                    }
                }
            }

            is KeysignState.Error -> {
                KeysignErrorScreen(
                    errorMessage = state.errorMessage,
                    tryAgain = onBack,
                    onBack = onBack.takeIf { hasBackClick },
                )
            }

            is KeysignState.WaitingForPeer -> {
                KeepScreenOn()
                VsSigningProgressIndicator(
                    text =
                        stringResource(
                            R.string.keysign_screen_waiting_for_peer,
                            state.missingPeers.joinToString(", "),
                        )
                )
            }

            else -> {
                KeepScreenOn()

                KeysignRiveProgress(progress = state.progress)
            }
        }
    }
}

@Composable
private fun KeysignRiveProgress(progress: Float) {
    val riveFile = rememberRiveResourceFile(resId = R.raw.riv_keysign).value
    if (riveFile == null) {
        VsSigningProgressIndicator(text = stringResource(R.string.keysign_screen_preparing_vault))
        return
    }
    val vmi =
        rememberViewModelInstance(
            file = riveFile,
            source = ViewModelSource.Named("ViewModel").defaultInstance(),
        )

    val animatedValue by
        animateFloatAsState(
            targetValue = progress.times(100),
            animationSpec = tween(durationMillis = 300),
            label = "riv_progress_animation",
        )

    SideEffect { vmi.setNumber(RIVE_PROGRESS_PROPERTY, animatedValue) }

    RiveAnimation(
        file = riveFile,
        viewModelInstance = vmi,
        modifier = Modifier.fillMaxSize(),
        fit = Fit.Cover(),
    )
}

@Preview
@Composable
private fun KeysignPreview() {
    KeysignView(
        state = KeysignState.KeysignFinished(TransactionStatus.Confirmed),
        progressLink = null,
        txHash = "0x1234567890",
        approveTransactionHash = "0x1234567890",
        transactionLink = "",
        approveTransactionLink = "",
        transactionTypeUiModel =
            TransactionTypeUiModel.Send(
                TransactionDetailsUiModel(
                    srcAddress = "0x1234567890",
                    dstAddress = "0x1234567890",
                    memo = "some memo",
                )
            ),
        onComplete = {},
        onAddToAddressBook = {},
        showSaveToAddressBook = true,
        hasBackClick = true,
    )
}

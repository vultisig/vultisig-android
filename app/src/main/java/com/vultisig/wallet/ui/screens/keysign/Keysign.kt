package com.vultisig.wallet.ui.screens.keysign

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.android.play.core.review.ReviewManagerFactory
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.AppVersionText
import com.vultisig.wallet.ui.components.KeepScreenOn
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.banners.Banner
import com.vultisig.wallet.ui.components.rive.RiveAnimation
import com.vultisig.wallet.ui.models.KeySignWrapperViewModel
import com.vultisig.wallet.ui.models.TransactionUiModel
import com.vultisig.wallet.ui.models.keysign.KeysignState
import com.vultisig.wallet.ui.models.keysign.KeysignViewModel
import com.vultisig.wallet.ui.models.keysign.TransactionTypeUiModel
import com.vultisig.wallet.ui.screens.TransactionDoneView
import com.vultisig.wallet.ui.screens.transaction.SwapTransactionOverviewScreen
import com.vultisig.wallet.ui.theme.Theme
import com.vultisig.wallet.ui.utils.showReviewPopUp

@Composable
internal fun Keysign(
    viewModel: KeysignViewModel,
    onError: (String) -> Unit,
    onComplete: () -> Unit,
    onKeysignFinished: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val reviewManager = remember { ReviewManagerFactory.create(context) }

    val wrapperViewModel = hiltViewModel(
        creationCallback = { factory: KeySignWrapperViewModel.Factory ->
            factory.create(viewModel)
        }
    )

    val keysignViewModel = wrapperViewModel.viewModel

    val state: KeysignState = keysignViewModel.currentState.collectAsState().value
    LaunchedEffect(state) {
        when (state) {

            is KeysignState.Error -> onError(state.errorMessage)
            is KeysignState.KeysignFinished -> {
                onKeysignFinished?.invoke()
                reviewManager.showReviewPopUp(context)
                wrapperViewModel.loadTransaction()
            }
            else -> Unit
        }
    }
    KeysignScreen(
        state = state,
        transactionTypeUiModel = wrapperViewModel.transactionUiModel.collectAsState().value,
        txHash = keysignViewModel.txHash.collectAsState().value,
        approveTransactionHash = keysignViewModel.approveTxHash.collectAsState().value,
        transactionLink = keysignViewModel.txLink.collectAsState().value,
        approveTransactionLink = keysignViewModel.approveTxLink.collectAsState().value,
        onComplete = onComplete,
        progressLink = keysignViewModel.swapProgressLink.collectAsState().value,
        onBack = {
            viewModel.navigateToHome()
        }
    )
}

@Composable
internal fun KeysignScreen(
    state: KeysignState,
    txHash: String,
    approveTransactionHash: String,
    transactionLink: String,
    approveTransactionLink: String,
    onComplete: () -> Unit,
    onBack: () -> Unit = {},
    progressLink: String?,
    transactionTypeUiModel: TransactionTypeUiModel?
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
                            showToolbar = false,
                            transactionHash = txHash,
                            approveTransactionHash = approveTransactionHash,
                            transactionLink = transactionLink,
                            approveTransactionLink = approveTransactionLink,
                            onComplete = onComplete,
                            progressLink = progressLink ?: "",
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

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            color = Theme.colors.backgrounds.primary,
                        )
                        .padding(
                            horizontal = 16.dp,
                            vertical = 24.dp,
                        ),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Banner(
                        text = stringResource(R.string.keysign_make_sure_devices_are_on_the_same_network),
                    )

                    UiSpacer(weight = 1f)

                    RiveAnimation(
                        animation = R.raw.riv_connecting_with_server,
                        modifier = Modifier
                            .size(24.dp)
                    )

                    UiSpacer(16.dp)

                    Text(
                        text = text,
                        color = Theme.colors.text.primary,
                        style = Theme.brockmann.headings.title2,
                        textAlign = TextAlign.Center,
                    )

                    UiSpacer(weight = 1f)

                    UiSpacer(size = 60.dp)

                    AppVersionText()
                }
            }
        }
    }
}

@Preview
@Composable
private fun KeysignPreview() {
    KeysignScreen(
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

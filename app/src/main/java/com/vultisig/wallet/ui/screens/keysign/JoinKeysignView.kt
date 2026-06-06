package com.vultisig.wallet.ui.screens.keysign

import android.content.Context
import android.net.nsd.NsdManager
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.errors.ErrorView
import com.vultisig.wallet.ui.components.errors.ErrorViewButtonUiModel
import com.vultisig.wallet.ui.components.v2.scaffold.V2Scaffold
import com.vultisig.wallet.ui.models.KeySignWrapperViewModel
import com.vultisig.wallet.ui.models.keysign.JoinKeysignError
import com.vultisig.wallet.ui.models.keysign.JoinKeysignState.DiscoverService
import com.vultisig.wallet.ui.models.keysign.JoinKeysignState.DiscoveringSessionID
import com.vultisig.wallet.ui.models.keysign.JoinKeysignState.Error
import com.vultisig.wallet.ui.models.keysign.JoinKeysignState.JoinKeysign
import com.vultisig.wallet.ui.models.keysign.JoinKeysignState.Keysign
import com.vultisig.wallet.ui.models.keysign.JoinKeysignState.QbtcClaim
import com.vultisig.wallet.ui.models.keysign.JoinKeysignState.WaitingForKeysignStart
import com.vultisig.wallet.ui.models.keysign.JoinKeysignViewModel
import com.vultisig.wallet.ui.models.keysign.KeysignState
import com.vultisig.wallet.ui.models.keysign.VerifyUiModel
import com.vultisig.wallet.ui.screens.deposit.VerifyDepositScreen
import com.vultisig.wallet.ui.screens.send.VerifySendScreen
import com.vultisig.wallet.ui.screens.sign.VerifySignMessageScreen
import com.vultisig.wallet.ui.screens.swap.VerifySwapScreen
import com.vultisig.wallet.ui.utils.asString

@Composable
internal fun JoinKeysignView() {
    val viewModel: JoinKeysignViewModel = hiltViewModel()
    val context = LocalContext.current
    var keysignState: KeysignState by remember { mutableStateOf(KeysignState.CreatingInstance) }

    if (keysignState is KeysignState.KeysignFinished) {
        viewModel.enableNavigationToHome()
    }
    val state by viewModel.currentState.collectAsState()
    val verifyUiModel by viewModel.verifyUiModel.collectAsState()
    val dappMetadata by viewModel.dappMetadata.collectAsState()
    val isKeysignFinished = keysignState is KeysignState.KeysignFinished
    val isKeysignInProgress = state == Keysign && keysignState.isInProgress
    val isSignMessageDone = isKeysignFinished && verifyUiModel is VerifyUiModel.SignMessage

    if (state == JoinKeysign) {
        // Each Verify*Screen owns its scaffold + toolbar, so the join path renders
        // them directly — avoids the double V2Scaffold padding that previously
        // pushed the verify card and its button off-position vs the initiator path.
        BackHandler(onBack = viewModel::navigateToHome)
        when (val model = verifyUiModel) {
            is VerifyUiModel.Send -> {
                VerifySendScreen(
                    state = model.model,
                    dappMetadata = dappMetadata,
                    isConsentsEnabled = false,
                    hasToolbar = true,
                    confirmTitle = stringResource(R.string.verify_swap_sign_button),
                    onBackClick = viewModel::navigateToHome,
                    onFastSignClick = {},
                    onConfirm = viewModel::joinKeysign,
                )
            }

            is VerifyUiModel.Swap -> {
                VerifySwapScreen(
                    state = model.model,
                    dappMetadata = dappMetadata,
                    hasToolbar = true,
                    onBackClick = viewModel::navigateToHome,
                    confirmTitle = stringResource(R.string.verify_swap_sign_button),
                    isConsentsEnabled = false,
                    onFastSignClick = {},
                    onConfirm = viewModel::joinKeysign,
                )
            }

            is VerifyUiModel.Deposit -> {
                VerifyDepositScreen(
                    state = model.model,
                    dappMetadata = dappMetadata,
                    hasToolbar = true,
                    confirmTitle = stringResource(R.string.verify_swap_sign_button),
                    onBackClick = viewModel::navigateToHome,
                    onFastSignClick = {},
                    onConfirm = viewModel::joinKeysign,
                )
            }

            is VerifyUiModel.SignMessage -> {
                VerifySignMessageScreen(
                    state = model.model,
                    hasToolbar = true,
                    confirmTitle = stringResource(R.string.verify_swap_sign_button),
                    onBackClick = viewModel::navigateToHome,
                    onFastSignClick = {},
                    onConfirm = viewModel::joinKeysign,
                )
            }
        }
        return
    }

    val title =
        stringResource(
            when {
                isSignMessageDone -> R.string.transaction_done_overview
                isKeysignFinished -> R.string.transaction_complete_screen_title
                else -> R.string.sign_transaction
            }
        )
    JoinKeysignScreen(
        title = title,
        onBack = viewModel::navigateToHome,
        isError = state is Error,
        fullScreen = isKeysignInProgress,
        applyDefaultPaddings = !isKeysignFinished,
    ) {
        when (state) {
            DiscoveringSessionID,
            WaitingForKeysignStart -> {
                val text =
                    when (state) {
                        DiscoveringSessionID ->
                            stringResource(R.string.join_keysign_discovering_session_id)
                        WaitingForKeysignStart ->
                            stringResource(R.string.join_keysign_waiting_keysign_start)
                        else -> ""
                    }
                KeysignLoadingScreen(text = text)
            }

            DiscoverService -> {
                val nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
                viewModel.discoveryMediator(nsdManager)
                KeysignLoadingScreen(text = stringResource(R.string.join_keysign_discovery_service))
            }

            JoinKeysign -> Unit // handled above, before the JoinKeysignScreen wrapper

            is QbtcClaim -> {
                val claim = state as QbtcClaim
                KeysignLoadingScreen(
                    text =
                        if (claim.txHash == null) stringResource(R.string.qbtc_claim_proving)
                        else stringResource(R.string.qbtc_claim_success_title)
                )
            }

            Keysign -> {
                val wrapperViewModel =
                    hiltViewModel(
                        creationCallback = { factory: KeySignWrapperViewModel.Factory ->
                            factory.create(viewModel.keysignViewModel)
                        }
                    )
                val keysignViewModel = wrapperViewModel.viewModel
                val kState = keysignViewModel.currentState.collectAsState().value
                keysignState = kState
                KeysignView(
                    state = kState,
                    txHash = keysignViewModel.txHash.collectAsState().value,
                    approveTransactionHash = keysignViewModel.approveTxHash.collectAsState().value,
                    transactionLink = keysignViewModel.txLink.collectAsState().value,
                    approveTransactionLink = keysignViewModel.approveTxLink.collectAsState().value,
                    progressLink = keysignViewModel.swapProgressLink.collectAsState().value,
                    onComplete = viewModel::complete,
                    onBack = keysignViewModel::navigateToHome,
                    transactionTypeUiModel =
                        keysignViewModel.resolvedTransactionUiModel.collectAsState().value,
                    onAddToAddressBook = keysignViewModel::navigateToAddressBook,
                    showSaveToAddressBook =
                        keysignViewModel.showSaveToAddressBook.collectAsState().value,
                    hasBackClick = false,
                    dappMetadata = dappMetadata,
                    coinLogoRes = keysignViewModel.coinLogoRes,
                )
            }

            is Error -> {
                val error = state as Error
                val errorLabel: String
                val buttonText: String
                val infoText: String?
                when (error.errorType) {
                    is JoinKeysignError.WrongVaultShare,
                    is JoinKeysignError.WrongVault -> {
                        errorLabel = error.errorType.message.asString()
                        buttonText =
                            stringResource(
                                R.string.join_keysign_error_wrong_vault_share_try_again_button
                            )
                        infoText = null
                    }

                    else -> {
                        errorLabel =
                            stringResource(
                                R.string.signing_error_please_try_again_s,
                                error.errorType.message.asString(),
                            )
                        buttonText = stringResource(R.string.try_again)
                        infoText = null
                    }
                }
                ErrorView(
                    title = errorLabel,
                    description = infoText,
                    buttonUiModel =
                        ErrorViewButtonUiModel(text = buttonText, onClick = viewModel::tryAgain),
                )
            }
        }
    }
}

@Composable
private fun JoinKeysignScreen(
    title: String,
    isError: Boolean,
    applyDefaultPaddings: Boolean = true,
    fullScreen: Boolean = false,
    onBack: () -> Unit = {},
    content: @Composable () -> Unit = {},
) {
    BackHandler(onBack = onBack)
    if (fullScreen) {
        content()
    } else {
        V2Scaffold(
            onBackClick = onBack.takeIf { !isError },
            rightIcon = R.drawable.big_close.takeIf { isError },
            onRightIconClick = onBack.takeIf { isError },
            title = title,
            applyDefaultPaddings = applyDefaultPaddings,
            content = content,
        )
    }
}

@Preview
@Composable
private fun JoinKeysignViewPreview() {
    JoinKeysignScreen(
        title = stringResource(R.string.keysign),
        isError = true,
        content = {
            ErrorView(
                title = stringResource(R.string.signing_error_please_try_again_s, ""),
                buttonUiModel =
                    ErrorViewButtonUiModel(text = stringResource(R.string.try_again), onClick = {}),
            )
        },
    )
}

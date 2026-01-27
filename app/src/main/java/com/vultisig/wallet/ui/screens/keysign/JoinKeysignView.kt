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
import androidx.navigation.NavHostController
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.errors.ErrorView
import com.vultisig.wallet.ui.components.scaffold.V2Scaffold
import com.vultisig.wallet.ui.components.scaffold.VsScaffold
import com.vultisig.wallet.ui.models.KeySignWrapperViewModel
import com.vultisig.wallet.ui.models.keysign.JoinKeysignError
import com.vultisig.wallet.ui.models.keysign.JoinKeysignState.DiscoverService
import com.vultisig.wallet.ui.models.keysign.JoinKeysignState.DiscoveringSessionID
import com.vultisig.wallet.ui.models.keysign.JoinKeysignState.Error
import com.vultisig.wallet.ui.models.keysign.JoinKeysignState.JoinKeysign
import com.vultisig.wallet.ui.models.keysign.JoinKeysignState.Keysign
import com.vultisig.wallet.ui.models.keysign.JoinKeysignState.WaitingForKeysignStart
import com.vultisig.wallet.ui.models.keysign.JoinKeysignViewModel
import com.vultisig.wallet.ui.models.keysign.KeysignState
import com.vultisig.wallet.ui.models.keysign.VerifyUiModel
import com.vultisig.wallet.ui.navigation.Route
import com.vultisig.wallet.ui.screens.deposit.VerifyDepositScreen
import com.vultisig.wallet.ui.screens.send.VerifySendScreen
import com.vultisig.wallet.ui.screens.sign.VerifySignMessageScreen
import com.vultisig.wallet.ui.screens.swap.VerifySwapScreen
import com.vultisig.wallet.ui.utils.asString

@Composable
internal fun JoinKeysignView(
    navController: NavHostController,
) {
    val viewModel: JoinKeysignViewModel = hiltViewModel()
    val context = LocalContext.current
    var keysignState: KeysignState by remember { mutableStateOf(KeysignState.CreatingInstance) }

    if (keysignState is KeysignState.KeysignFinished) {
        viewModel.enableNavigationToHome()
    }
    val state = viewModel.currentState.value
    JoinKeysignScreen(
        isKeySignFinished = keysignState is KeysignState.KeysignFinished,
        onBack = viewModel::navigateToHome,
        isError = state is Error
    ) {
        when (state) {
            DiscoveringSessionID,
            WaitingForKeysignStart,
                -> {
                val text = when (state) {
                    DiscoveringSessionID -> stringResource(R.string.join_keysign_discovering_session_id)
                    WaitingForKeysignStart -> stringResource(R.string.join_keysign_waiting_keysign_start)
                    else -> ""
                }
                KeysignLoadingScreen(
                    text = text,
                )
            }

            DiscoverService -> {
                val nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
                viewModel.discoveryMediator(nsdManager)
                KeysignLoadingScreen(
                    text = stringResource(R.string.join_keysign_discovery_service),
                )
            }

            JoinKeysign -> {
                val verifyUiModel by viewModel.verifyUiModel.collectAsState()

                when (val model = verifyUiModel) {
                    is VerifyUiModel.Send -> {
                        VerifySendScreen(
                            state = model.model,
                            isConsentsEnabled = false,
                            confirmTitle = stringResource(R.string.verify_transaction_join_keysign),
                            onFastSignClick = {},
                            onConfirm = viewModel::joinKeysign,
                        )
                    }

                    is VerifyUiModel.Swap -> {
                        VerifySwapScreen(
                            state = model.model,
                            showToolbar = false,
                            onBackClick = {},
                            confirmTitle = stringResource(R.string.verify_swap_sign_button),
                            isConsentsEnabled = false,
                            onFastSignClick = {},
                            onConfirm = viewModel::joinKeysign,
                        )
                    }

                    is VerifyUiModel.Deposit -> {
                        VerifyDepositScreen(
                            state = model.model,
                            confirmTitle = stringResource(R.string.verify_swap_sign_button),
                            onFastSignClick = {},
                            onConfirm = viewModel::joinKeysign
                        )
                    }

                    is VerifyUiModel.SignMessage -> {
                        VerifySignMessageScreen(
                            state = model.model,
                            confirmTitle = stringResource(R.string.verify_swap_sign_button),
                            onFastSignClick = {},
                            onConfirm = viewModel::joinKeysign
                        )
                    }
                }
            }

            Keysign -> {
                val wrapperViewModel = hiltViewModel(
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
                    onComplete = {
                        navController.navigate(Route.Home())
                    },
                    onBack = keysignViewModel::navigateToHome,
                    transactionTypeUiModel = keysignViewModel.transactionTypeUiModel,
                    onAddToAddressBook = keysignViewModel::navigateToAddressBook,
                    showSaveToAddressBook = keysignViewModel.showSaveToAddressBook.collectAsState().value,
                )
            }

            is Error -> {
                val errorLabel: String
                val buttonText: String
                val infoText: String?
                when (state.errorType) {
                    is JoinKeysignError.WrongVaultShare, is JoinKeysignError.WrongVault -> {
                        errorLabel = state.errorType.message.asString()
                        buttonText =
                            stringResource(R.string.join_keysign_error_wrong_vault_share_try_again_button)
                        infoText = null
                    }

                    else -> {
                        errorLabel = stringResource(
                            R.string.signing_error_please_try_again_s,
                            state.errorType.message.asString()
                        )
                        buttonText = stringResource(R.string.try_again)
                        infoText = stringResource(R.string.bottom_warning_msg_keygen_error_screen)
                    }
                }
                ErrorView(
                    title = errorLabel,
                    buttonText = buttonText,
                    description = infoText,
                    onButtonClick = viewModel::tryAgain,
                )
            }
        }
    }
}

@Composable
private fun JoinKeysignScreen(
    isKeySignFinished: Boolean,
    isError: Boolean,
    onBack: () -> Unit = {},
    content: @Composable () -> Unit = {},
) {
    BackHandler(onBack = onBack)
    VsScaffold(
        onBackClick = onBack.takeIf { isKeySignFinished.not() && isError.not()},
        rightIcon = R.drawable.big_close.takeIf { isError },
        onRightIconClick = onBack.takeIf { isError },
        title =  stringResource(
            id = if (isKeySignFinished.not()) R.string.keysign
            else R.string.transaction_complete_screen_title
        ),
        content = content,
    )
}

@Preview
@Composable
private fun JoinKeysignViewPreview() {
    JoinKeysignScreen(
        isKeySignFinished = false,
        isError = true,
        content = {
            ErrorView(
                title = stringResource(R.string.signing_error_please_try_again_s, ""),
                buttonText = stringResource(R.string.try_again),
                onButtonClick = {},
            )
        }
    )
}
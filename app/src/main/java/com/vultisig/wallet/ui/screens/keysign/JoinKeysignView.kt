package com.vultisig.wallet.ui.screens.keysign

import ErrorView
import android.content.Context
import android.net.nsd.NsdManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.BoxScope
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
import androidx.navigation.compose.rememberNavController
import com.vultisig.wallet.R
import com.vultisig.wallet.common.asString
import com.vultisig.wallet.presenter.common.KeepScreenOn
import com.vultisig.wallet.presenter.keysign.JoinKeysignError
import com.vultisig.wallet.presenter.keysign.JoinKeysignState
import com.vultisig.wallet.presenter.keysign.JoinKeysignState.DiscoverService
import com.vultisig.wallet.presenter.keysign.JoinKeysignState.DiscoveringSessionID
import com.vultisig.wallet.presenter.keysign.JoinKeysignState.Error
import com.vultisig.wallet.presenter.keysign.JoinKeysignState.JoinKeysign
import com.vultisig.wallet.presenter.keysign.JoinKeysignState.Keysign
import com.vultisig.wallet.presenter.keysign.JoinKeysignState.WaitingForKeysignStart
import com.vultisig.wallet.presenter.keysign.JoinKeysignViewModel
import com.vultisig.wallet.presenter.keysign.KeysignState
import com.vultisig.wallet.presenter.keysign.VerifyUiModel
import com.vultisig.wallet.ui.components.ProgressScreen
import com.vultisig.wallet.ui.models.KeySignWrapperViewModel
import com.vultisig.wallet.ui.navigation.Screen
import com.vultisig.wallet.ui.screens.deposit.VerifyDepositScreen
import com.vultisig.wallet.ui.screens.send.VerifyTransactionScreen
import com.vultisig.wallet.ui.screens.swap.VerifySwapScreen

@Composable
internal fun JoinKeysignView(
    navController: NavHostController,
) {
    KeepScreenOn()

    val viewModel: JoinKeysignViewModel = hiltViewModel()
    val context = LocalContext.current
    var keysignState by remember { mutableStateOf(KeysignState.CreatingInstance) }

    if (keysignState == KeysignState.KeysignFinished) {
        viewModel.enableNavigationToHome()
    }
    JoinKeysignScreen(
        navController = navController,
        state = viewModel.currentState.value,
        keysignState = keysignState,
        onBack = viewModel::navigateToHome
        ) { state ->
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
                        VerifyTransactionScreen(
                            state = model.model,
                            isConsentsEnabled = false,
                            confirmTitle = stringResource(R.string.verify_transaction_join_keysign),
                            onConfirm = viewModel::joinKeysign,
                        )
                    }

                    is VerifyUiModel.Swap -> {
                        VerifySwapScreen(
                            state = model.model,
                            confirmTitle = stringResource(R.string.verify_swap_sign_button),
                            isConsentsEnabled = false,
                            onConfirm = viewModel::joinKeysign,
                        )
                    }

                    is VerifyUiModel.Deposit -> {
                        VerifyDepositScreen(
                            state = model.model,
                            confirmTitle = stringResource(R.string.verify_swap_sign_button),
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
                KeysignScreen(
                    state = kState,
                    errorMessage = keysignViewModel.errorMessage.value,
                    txHash = keysignViewModel.txHash.collectAsState().value,
                    transactionLink = keysignViewModel.txLink.collectAsState().value,
                    isThorChainSwap = keysignViewModel.isThorChainSwap,
                    onComplete = {
                        navController.navigate(Screen.Home.route)
                    },
                    onBack = keysignViewModel::navigateToHome
                )
            }

            is Error -> {
                ErrorView(
                    errorLabel = stringResource(
                        R.string.signing_error_please_try_again_s,
                        state.errorType.message.asString()
                    ),
                    buttonText = stringResource(R.string.try_again),
                    infoText = stringResource(R.string.bottom_warning_msg_keygen_error_screen),
                    onButtonClick = viewModel::tryAgain,
                )
            }
        }
    }
}

@Composable
private fun JoinKeysignScreen(
    navController: NavHostController,
    state: JoinKeysignState,
    keysignState: KeysignState,
    onBack: () -> Unit = {},
    content: @Composable BoxScope.(JoinKeysignState) -> Unit = {},

    ) {
    BackHandler(onBack = onBack)
    ProgressScreen(
        navController = navController,
        title = stringResource(
            id = if (keysignState != KeysignState.KeysignFinished) R.string.keysign
            else R.string.transaction_done_title
        ),
        onStartIconClick = onBack,
        progress = when (state) {
            DiscoveringSessionID -> 0.1f
            DiscoverService -> 0.25f
            JoinKeysign -> 0.5f
            WaitingForKeysignStart -> 0.625f
            Keysign -> 0.75f
            is Error -> 0.0f
        },
        content = { content(state) }
    )
}

@Preview
@Composable
private fun JoinKeysignViewPreview() {
    JoinKeysignScreen(
        navController = rememberNavController(),
        state = Error(errorType = JoinKeysignError.WrongVault),
        keysignState = KeysignState.CreatingInstance,
    )
}
package com.vultisig.wallet.ui.screens.keysign

import android.content.Context
import android.net.nsd.NsdManager
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
import com.vultisig.wallet.presenter.common.KeepScreenOn
import com.vultisig.wallet.presenter.keysign.JoinKeysignState
import com.vultisig.wallet.presenter.keysign.JoinKeysignState.DiscoverService
import com.vultisig.wallet.presenter.keysign.JoinKeysignState.DiscoveryingSessionID
import com.vultisig.wallet.presenter.keysign.JoinKeysignState.Error
import com.vultisig.wallet.presenter.keysign.JoinKeysignState.FailedToStart
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

    JoinKeysignScreen(
        navController = navController,
        state = viewModel.currentState.value,
        keysignState = keysignState,
    ) { state ->
        when (state) {
            DiscoveryingSessionID,
            WaitingForKeysignStart,
            -> {
                val text = when (state) {
                    DiscoveryingSessionID -> stringResource(R.string.join_keysign_discovering_session_id)
                    WaitingForKeysignStart -> stringResource(R.string.joinkeysign_waiting_keysign_start)
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
                    text = stringResource(R.string.joinkeysign_discovery_service),
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
                    }
                )
            }

            FailedToStart, Error -> {
                KeysignErrorView(
                    navController = navController,
                    errorMessage = viewModel.errorMessage.value,
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
    content: @Composable BoxScope.(JoinKeysignState) -> Unit = {},
) {
    ProgressScreen(
        navController = navController,
        title = stringResource(
            id = if (keysignState != KeysignState.KeysignFinished) R.string.keysign
            else R.string.transaction_done_title
        ),
        progress = when (state) {
            DiscoveryingSessionID -> 0.1f
            DiscoverService -> 0.25f
            JoinKeysign -> 0.5f
            WaitingForKeysignStart -> 0.625f
            Keysign -> 0.75f
            FailedToStart, Error -> 0.0f
        },
        content = { content(state) }
    )
}

@Preview
@Composable
private fun JoinKeysignViewPreview() {
    JoinKeysignScreen(
        navController = rememberNavController(),
        state = Error,
        keysignState = KeysignState.CreatingInstance,
    )
}
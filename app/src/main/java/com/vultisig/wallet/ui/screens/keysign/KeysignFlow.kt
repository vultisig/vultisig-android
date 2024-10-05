package com.vultisig.wallet.ui.screens.keysign

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.vultisig.wallet.app.activity.MainActivity
import com.vultisig.wallet.ui.components.KeepScreenOn
import com.vultisig.wallet.ui.models.keysign.KeysignFlowState
import com.vultisig.wallet.ui.models.keysign.KeysignFlowViewModel
import com.vultisig.wallet.ui.models.keysign.KeysignShareViewModel

@Suppress("ReplaceNotNullAssertionWithElvisReturn")
@Composable
fun KeysignFlowView(
    navController: NavController,
    onComplete: () -> Unit,
    onKeysignFinished: (() -> Unit)? = null,
) {
    KeepScreenOn()

    val viewModel: KeysignFlowViewModel = hiltViewModel()
    val sharedViewModel: KeysignShareViewModel = hiltViewModel(LocalContext.current as MainActivity)
    if (sharedViewModel.vault == null || sharedViewModel.keysignPayload == null) {
        // information is not available, go back
        viewModel.moveToState(KeysignFlowState.ERROR)
    }
    
    when (viewModel.currentState.collectAsState().value) {
        KeysignFlowState.PEER_DISCOVERY -> {
            KeysignPeerDiscovery(
                viewModel
            )
        }

        KeysignFlowState.KEYSIGN -> {
            LaunchedEffect(key1 = Unit) {
                // TODO this breaks the navigation, and introduces issue with multiple
                //   keysignViewModels being created
                // viewModel.resetQrAddress()
            }

            Keysign(
                viewModel = viewModel.keysignViewModel,
                onComplete = onComplete,
                onKeysignFinished = onKeysignFinished,
            )
        }

        KeysignFlowState.ERROR -> {
            KeysignErrorScreen(
                errorMessage = viewModel.errorMessage.value,
                tryAgain = viewModel::tryAgain,
            )
        }

    }
}
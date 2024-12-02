package com.vultisig.wallet.ui.screens.keysign

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import com.vultisig.wallet.app.activity.MainActivity
import com.vultisig.wallet.ui.models.keysign.KeysignFlowState
import com.vultisig.wallet.ui.models.keysign.KeysignFlowViewModel
import com.vultisig.wallet.ui.models.keysign.KeysignShareViewModel

@Suppress("ReplaceNotNullAssertionWithElvisReturn")
@Composable
fun KeysignFlowView(
    onComplete: () -> Unit,
    onKeysignFinished: (() -> Unit)? = null,
) {
    val viewModel: KeysignFlowViewModel = hiltViewModel()
    val sharedViewModel: KeysignShareViewModel = hiltViewModel(LocalContext.current as MainActivity)
    val keysignFlowState by viewModel.currentState.collectAsState()
    if (sharedViewModel.vault == null || sharedViewModel.keysignPayload == null) {
        // information is not available, go back
        viewModel.moveToState(KeysignFlowState.Error("Keysign information not available"))
    }

    when (keysignFlowState) {
        is KeysignFlowState.PeerDiscovery -> {
            KeysignPeerDiscovery(
                viewModel
            )
        }

        is KeysignFlowState.Keysign -> {
            LaunchedEffect(key1 = Unit) {
                // TODO this breaks the navigation, and introduces issue with multiple
                //   keysignViewModels being created
                // viewModel.resetQrAddress()
            }

            Keysign(
                viewModel = viewModel.keysignViewModel,
                onError = { viewModel.moveToState(KeysignFlowState.Error(it)) },
                onComplete = onComplete,
                onKeysignFinished = onKeysignFinished,
            )
        }

        is KeysignFlowState.Error -> {
            KeysignErrorScreen(
                errorMessage = (keysignFlowState as? KeysignFlowState.Error)?.errorMessage?:  "",
                tryAgain = viewModel::tryAgain,
            )
        }
    }
}
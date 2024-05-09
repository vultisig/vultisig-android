package com.vultisig.wallet.presenter.keysign

import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.vultisig.wallet.app.activity.MainActivity

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun KeysignFlowView(navController: NavController) {
    val viewModel: KeysignFlowViewModel = hiltViewModel()
    val sharedViewModel: KeysignShareViewModel = viewModel(LocalContext.current as MainActivity)
    if (sharedViewModel.vault == null || sharedViewModel.keysignPayload == null) {
        // information is not available, go back
        viewModel.moveToState(KeysignFlowState.ERROR)
    }
    when (viewModel.currentState.value) {
        KeysignFlowState.PEER_DISCOVERY -> {
            KeysignPeerDiscovery(
                navController,
                sharedViewModel.vault!!,
                sharedViewModel.keysignPayload!!,
                viewModel
            )
        }

        KeysignFlowState.KEYSIGN -> {
            Keysign(navController, viewModel.keysignViewModel)
        }

        KeysignFlowState.ERROR -> {
            KeysignErrorScreen(navController)
        }


    }
}
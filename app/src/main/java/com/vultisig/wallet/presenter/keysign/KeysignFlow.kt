package com.vultisig.wallet.presenter.keysign

import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.vultisig.wallet.app.activity.MainActivity
import com.vultisig.wallet.ui.screens.keysign.Keysign
import com.vultisig.wallet.ui.screens.keysign.KeysignErrorScreen

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun KeysignFlowView(navController: NavController) {
    val viewModel: KeysignFlowViewModel = hiltViewModel()
    val context = LocalContext.current.applicationContext
    val sharedViewModel: KeysignShareViewModel = hiltViewModel(LocalContext.current as MainActivity)
    if (sharedViewModel.vault == null || sharedViewModel.keysignPayload == null) {
        // information is not available, go back
        viewModel.moveToState(KeysignFlowState.ERROR)
    }
    DisposableEffect(key1 = Unit) {
        onDispose {
            viewModel.stopService(context)
        }
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
            LaunchedEffect(key1 = Unit) {
                viewModel.startKeysign()
            }

            Keysign(navController, viewModel.keysignViewModel)
        }

        KeysignFlowState.ERROR -> {
            KeysignErrorScreen(navController)
        }

    }
}
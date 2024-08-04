package com.vultisig.wallet.presenter.keysign

import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.vultisig.wallet.R
import com.vultisig.wallet.app.activity.MainActivity
import com.vultisig.wallet.common.asUiText
import com.vultisig.wallet.presenter.common.KeepScreenOn
import com.vultisig.wallet.ui.navigation.Screen
import com.vultisig.wallet.ui.screens.keysign.Keysign
import com.vultisig.wallet.ui.screens.keysign.KeysignErrorScreen
import com.vultisig.wallet.ui.screens.keysign.KeysignPeerDiscovery

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun KeysignFlowView(
    navController: NavController,
    onComplete: () -> Unit,
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
                sharedViewModel.vault!!,
                sharedViewModel.keysignPayload!!,
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
            )
        }

        KeysignFlowState.ERROR -> {
            KeysignErrorScreen(
                navController = navController,
                errorMessage = viewModel.errorMessage.value,
                onTryAgain = viewModel::onTryAgain,
            )
        }

    }
}
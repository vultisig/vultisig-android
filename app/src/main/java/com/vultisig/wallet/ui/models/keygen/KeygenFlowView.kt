package com.vultisig.wallet.ui.models.keygen

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.KeepScreenOn
import com.vultisig.wallet.ui.screens.keygen.GeneratingKey
import com.vultisig.wallet.ui.screens.keygen.KeygenPeerDiscovery

@Composable
fun KeygenFlowView(
    navController: NavHostController,
) {
    KeepScreenOn()
    val viewModel: KeygenFlowViewModel = hiltViewModel()
    val uiState = viewModel.uiState.collectAsState()
    when (uiState.value.currentState) {
        KeygenFlowState.PEER_DISCOVERY -> {
            KeygenPeerDiscovery(navController, viewModel)
        }

        KeygenFlowState.DEVICE_CONFIRMATION -> {
            DeviceList(navController, viewModel)
        }

        KeygenFlowState.KEYGEN -> {
            GeneratingKey(navController, viewModel.generatingKeyViewModel)
        }

        KeygenFlowState.ERROR -> {
            KeyGenErrorScreen(navController)
        }

        KeygenFlowState.SUCCESS -> {
            Text(text = stringResource(R.string.keygen_flow_views_success))
        }
    }
}
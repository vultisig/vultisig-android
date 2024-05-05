package com.vultisig.wallet.presenter.keygen

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.vultisig.wallet.models.Vault

@Composable
fun KeygenFlowView(navController: NavHostController, vault: Vault) {
    val viewModel: KeygenFlowViewModel = hiltViewModel()
    when (viewModel.currentState.value) {
        KeygenFlowState.PEER_DISCOVERY -> {
            KeygenPeerDiscovery(navController, vault, viewModel)
        }

        KeygenFlowState.DEVICE_CONFIRMATION -> {
            DeviceList(navController,viewModel)
        }

        KeygenFlowState.KEYGEN -> {
            GeneratingKey(navController,viewModel.generatingKeyViewModel)
        }

        KeygenFlowState.ERROR -> {
            Text(text = "Error")
        }

        KeygenFlowState.SUCCESS -> {
            Text(text = "Success")
        }
    }
}
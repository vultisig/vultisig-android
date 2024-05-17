package com.vultisig.wallet.presenter.keysign

import android.content.Context
import android.net.nsd.NsdManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.screens.VerifyTransactionScreen
import com.vultisig.wallet.ui.screens.keysign.Keysign
import com.vultisig.wallet.ui.screens.keysign.KeysignErrorScreen
import com.vultisig.wallet.ui.screens.keysign.KeysignLoadingScreen
import com.vultisig.wallet.ui.screens.keysign.KeysignSessionIdDiscoveryScreen

@Composable
internal fun JoinKeysignView(
    navController: NavHostController,
    qrCodeResult: String? = null,
) {
    val viewModel: JoinKeysignViewModel = hiltViewModel()
    val context = LocalContext.current

    LaunchedEffect(qrCodeResult) {
        if (qrCodeResult != null) {
            viewModel.setScanResult(qrCodeResult)
        } else {
            viewModel.startScan()
        }
    }

    LaunchedEffect(key1 = Unit) {
        viewModel.setData()
    }
    DisposableEffect(key1 = Unit) {
        onDispose {
            viewModel.cleanUp()
        }
    }
    when (viewModel.currentState.value) {
        JoinKeysignState.DiscoveryingSessionID -> {
            KeysignSessionIdDiscoveryScreen(navController = navController)
        }

        JoinKeysignState.DiscoverService -> {
            val nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
            viewModel.discoveryMediator(nsdManager)
            KeysignDiscoverService(navController = navController)
        }

        JoinKeysignState.JoinKeysign -> {
            val transaction = viewModel.transactionUiModel.collectAsState().value
            VerifyTransactionScreen(
                navController = navController,
                state = transaction,
                isConsentsEnabled = false,
                confirmTitle = stringResource(R.string.verify_transaction_join_keysign),
                onConfirm = viewModel::joinKeysign,
            )
        }

        JoinKeysignState.WaitingForKeysignStart -> {
            WaitingForKeysignToStart(navController = navController)
        }

        JoinKeysignState.Keysign -> {
            Keysign(navController = navController, viewModel = viewModel.keysignViewModel)
        }

        JoinKeysignState.FailedToStart, JoinKeysignState.Error -> {
            KeysignErrorScreen(
                navController = navController,
                errorMessage = viewModel.errorMessage.value
            )
        }
    }
}

@Composable
private fun KeysignDiscoverService(navController: NavHostController) {
    KeysignLoadingScreen(
        navController = navController,
        text = stringResource(R.string.joinkeysign_discovery_service),
    )
}

@Composable
private fun WaitingForKeysignToStart(navController: NavHostController) {
    KeysignLoadingScreen(
        navController = navController,
        text = stringResource(R.string.joinkeysign_waiting_keysign_start),
    )
}
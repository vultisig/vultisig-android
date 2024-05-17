package com.vultisig.wallet.presenter.keysign

import android.content.Context
import android.net.nsd.NsdManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.UiBarContainer
import com.vultisig.wallet.ui.screens.VerifyTransactionScreen
import com.vultisig.wallet.ui.screens.keysign.KeysignLoadingScreen
import com.vultisig.wallet.ui.screens.keysign.KeysignSessionIdDiscoveryScreen
import com.vultisig.wallet.ui.theme.Theme

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
            LaunchedEffect(key1 = viewModel) {
                viewModel.waitForKeysignToStart()
            }
            WaitingForKeysignToStart(navController = navController)
        }

        JoinKeysignState.Keysign -> {
            Keysign(navController = navController, viewModel = viewModel.keysignViewModel)
        }

        JoinKeysignState.FailedToStart -> {
            KeysignFailedToStart(
                navController = navController,
                errorMessage = viewModel.errorMessage.value
            )
        }

        JoinKeysignState.Error -> {
            KeysignFailedToStart(
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

@Composable
private fun KeysignFailedToStart(navController: NavHostController, errorMessage: String) {
    UiBarContainer(
        navController = navController,
        title = stringResource(id = R.string.keysign)
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = "${stringResource(id = R.string.joinkeysign_fail_to_start)}$errorMessage",
                color = Theme.colors.neutral0,
                style = Theme.menlo.bodyMedium
            )
        }
    }
}

package com.vultisig.wallet.ui.screens.keysign

import android.content.Context
import android.net.nsd.NsdManager
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.vultisig.wallet.R
import com.vultisig.wallet.presenter.keysign.JoinKeysignState
import com.vultisig.wallet.presenter.keysign.JoinKeysignState.DiscoverService
import com.vultisig.wallet.presenter.keysign.JoinKeysignState.DiscoveryingSessionID
import com.vultisig.wallet.presenter.keysign.JoinKeysignState.Error
import com.vultisig.wallet.presenter.keysign.JoinKeysignState.FailedToStart
import com.vultisig.wallet.presenter.keysign.JoinKeysignState.JoinKeysign
import com.vultisig.wallet.presenter.keysign.JoinKeysignState.Keysign
import com.vultisig.wallet.presenter.keysign.JoinKeysignState.WaitingForKeysignStart
import com.vultisig.wallet.presenter.keysign.JoinKeysignViewModel
import com.vultisig.wallet.ui.components.UiBarContainer
import com.vultisig.wallet.ui.components.UiLinearProgressIndicator
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.navigation.Screen
import com.vultisig.wallet.ui.screens.VerifyTransactionScreen

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

    JoinKeysignScreen(
        navController = navController,
        state = viewModel.currentState.value,
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
                val transactionUiModel = viewModel.transactionUiModel.collectAsState().value

                VerifyTransactionScreen(
                    state = transactionUiModel,
                    isProgressEnabled = false,
                    isConsentsEnabled = false,
                    confirmTitle = stringResource(R.string.verify_transaction_join_keysign),
                    onConfirm = viewModel::joinKeysign,
                )
            }

            Keysign -> {
                val keysignViewModel = remember { viewModel.keysignViewModel }
                val hasStartedKeysign = remember { mutableStateOf(false) }
                if (!hasStartedKeysign.value) {
                    keysignViewModel.startKeysign()
                    hasStartedKeysign.value = true
                }
                KeysignScreen(
                    state = keysignViewModel.currentState.collectAsState().value,
                    errorMessage = keysignViewModel.errorMessage.value,
                    txHash = keysignViewModel.txHash.collectAsState().value,
                    transactionLink = keysignViewModel.txLink.collectAsState().value,
                    onComplete = {
                        navController.navigate(Screen.VaultDetail.createRoute(viewModel.vaultId))
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
    content: @Composable BoxScope.(JoinKeysignState) -> Unit = {},
) {
    UiBarContainer(
        navController = navController,
        title = stringResource(id = R.string.keysign)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            UiSpacer(size = 16.dp)

            UiLinearProgressIndicator(
                progress = when (state) {
                    DiscoveryingSessionID -> 0.1f
                    DiscoverService -> 0.25f
                    JoinKeysign -> 0.5f
                    WaitingForKeysignStart -> 0.625f
                    Keysign -> 0.75f
                    FailedToStart, Error -> 0.0f
                },
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            UiSpacer(size = 32.dp)

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                content(state)
            }
        }
    }
}

@Preview
@Composable
private fun JoinKeysignViewPreview() {
    JoinKeysignScreen(
        navController = rememberNavController(),
        state = Error,
    )
}
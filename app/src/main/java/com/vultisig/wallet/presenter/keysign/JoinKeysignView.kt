package com.vultisig.wallet.presenter.keysign

import android.app.Activity
import android.content.Context
import android.net.nsd.NsdManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.google.zxing.integration.android.IntentIntegrator
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.MultiColorButton
import com.vultisig.wallet.ui.components.TopBar
import com.vultisig.wallet.ui.theme.Theme
import com.vultisig.wallet.ui.theme.dimens
import kotlinx.coroutines.launch

@Composable
fun JoinKeysignView(
    navController: NavHostController,
) {
    val viewModel: JoinKeysignViewModel = hiltViewModel()
    val context = LocalContext.current
    val scanQrCodeLauncher =
        rememberLauncherForActivityResult(contract = ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val scanResult =
                    IntentIntegrator.parseActivityResult(result.resultCode, result.data)
                val qrCodeContent = scanResult.contents
                viewModel.setScanResult(qrCodeContent)
            }
        }
    LaunchedEffect(key1 = Unit) {
        viewModel.setData()
        val integrator = IntentIntegrator(context as Activity)
        integrator.setBarcodeImageEnabled(true)
        integrator.setOrientationLocked(true)
        integrator.setPrompt("Scan the QR code on your main device")
        scanQrCodeLauncher.launch(integrator.createScanIntent())
    }
    DisposableEffect(key1 = Unit) {
        onDispose {
            viewModel.cleanUp()
        }
    }
    when (viewModel.currentState.value) {
        JoinKeysignState.DiscoveryingSessionID -> {
            KeysignDiscoveryingSessionID(navController = navController)
        }

        JoinKeysignState.DiscoverService -> {
            val nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
            viewModel.discoveryMediator(nsdManager)
            KeysignDiscoverService(navController = navController)
        }

        JoinKeysignState.JoinKeysign -> {
            JoiningKeysign(navController = navController, viewModel = viewModel)
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
fun KeysignDiscoveryingSessionID(navController: NavHostController) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .background(Theme.colors.oxfordBlue800)
            .padding(
                vertical = MaterialTheme.dimens.marginMedium,
                horizontal = MaterialTheme.dimens.marginSmall
            )
    ) {
        TopBar(
            centerText = stringResource(id = R.string.keysign), startIcon = R.drawable.caret_left,
            navController = navController
        )
        Spacer(modifier = Modifier.height(MaterialTheme.dimens.medium2))
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Discovering Session ID",
                color = Theme.colors.neutral0,
                style = Theme.menlo.bodyMedium
            )
            CircularProgressIndicator(
                color = Theme.colors.neutral0,
                modifier = Modifier.padding(MaterialTheme.dimens.marginMedium)
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun PreviewDiscoveryingSessionID() {
    val navController = rememberNavController()
    KeysignDiscoveryingSessionID(navController = navController)
}

@Composable
fun KeysignDiscoverService(navController: NavHostController) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .background(Theme.colors.oxfordBlue800)
            .padding(
                vertical = MaterialTheme.dimens.marginMedium,
                horizontal = MaterialTheme.dimens.marginSmall
            )
    ) {
        TopBar(
            centerText = stringResource(id = R.string.keysign), startIcon = R.drawable.caret_left,
            navController = navController
        )
        Spacer(modifier = Modifier.height(MaterialTheme.dimens.medium2))
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = stringResource(id = R.string.joinkeysign_discovery_service),
                color = Theme.colors.neutral0,
                style = Theme.menlo.bodyMedium
            )
            CircularProgressIndicator(
                color = Theme.colors.neutral0,
                modifier = Modifier.padding(MaterialTheme.dimens.marginMedium)
            )
        }
    }
}

@Composable
internal fun JoiningKeysign(navController: NavHostController, viewModel: JoinKeysignViewModel) {
    val coroutineScope = rememberCoroutineScope()
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .background(Theme.colors.oxfordBlue800)
            .padding(
                vertical = MaterialTheme.dimens.marginMedium,
                horizontal = MaterialTheme.dimens.marginSmall
            )
            .fillMaxSize()
    ) {
        TopBar(
            centerText = stringResource(id = R.string.keysign), startIcon = R.drawable.caret_left,
            navController = navController
        )
        Spacer(modifier = Modifier.height(MaterialTheme.dimens.medium2))
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = MaterialTheme.dimens.marginMedium),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = stringResource(id = R.string.joinkeysign_from),
                    color = Theme.colors.neutral0,
                    style = Theme.menlo.bodyMedium,
                    modifier = Modifier.align(Alignment.CenterVertically)
                )
                Text(
                    text = viewModel.keysignPayload?.coin?.address ?: "",
                    color = Theme.colors.neutral0,
                    style = Theme.montserrat.body1,
                    modifier = Modifier.align(Alignment.CenterVertically)
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = MaterialTheme.dimens.marginMedium),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = stringResource(id = R.string.joinkeysign_to),
                    color = Theme.colors.neutral0,
                    style = Theme.menlo.bodyMedium,
                    modifier = Modifier.align(Alignment.CenterVertically)
                )
                Text(
                    text = viewModel.keysignPayload?.toAddress ?: "",
                    color = Theme.colors.neutral0,
                    style = Theme.montserrat.body1,
                    modifier = Modifier.align(Alignment.CenterVertically)
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = MaterialTheme.dimens.marginMedium),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = stringResource(id = R.string.joinkeysign_amount),
                    color = Theme.colors.neutral0,
                    style = Theme.menlo.bodyMedium,
                    modifier = Modifier.align(Alignment.CenterVertically)
                )
                Text(
                    text = viewModel.keysignPayload?.toAmount.toString(),
                    color = Theme.colors.neutral0,
                    style = Theme.montserrat.body1,
                    modifier = Modifier.align(Alignment.CenterVertically)
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            MultiColorButton(
                text = stringResource(id = R.string.joinkeysign),
                minHeight = MaterialTheme.dimens.minHeightButton,
                backgroundColor = Theme.colors.turquoise800,
                textColor = Theme.colors.oxfordBlue800,
                iconColor = Theme.colors.turquoise800,
                textStyle = Theme.montserrat.titleLarge,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = MaterialTheme.dimens.marginMedium,
                        end = MaterialTheme.dimens.marginMedium,
                        bottom = MaterialTheme.dimens.marginMedium,
                    )
            ) {
                coroutineScope.launch {
                    viewModel.joinKeysign()
                }
            }
        }

    }
}

@Composable
fun WaitingForKeysignToStart(navController: NavHostController) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .background(Theme.colors.oxfordBlue800)
            .padding(
                vertical = MaterialTheme.dimens.marginMedium,
                horizontal = MaterialTheme.dimens.marginSmall
            )
    ) {
        TopBar(
            centerText = stringResource(id = R.string.keysign), startIcon = R.drawable.caret_left,
            navController = navController
        )
        Spacer(modifier = Modifier.height(MaterialTheme.dimens.medium2))
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = stringResource(id = R.string.joinkeysign_waiting_keysign_start),
                color = Theme.colors.neutral0,
                style = Theme.menlo.bodyMedium
            )
            CircularProgressIndicator(
                color = Theme.colors.neutral0,
                modifier = Modifier.padding(MaterialTheme.dimens.marginMedium)
            )
        }
    }
}

@Composable
fun KeysignFailedToStart(navController: NavHostController, errorMessage: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .background(Theme.colors.oxfordBlue800)
            .padding(
                vertical = MaterialTheme.dimens.marginMedium,
                horizontal = MaterialTheme.dimens.marginSmall
            )
    ) {
        TopBar(
            centerText = stringResource(id = R.string.keysign), startIcon = R.drawable.caret_left,
            navController = navController
        )
        Spacer(modifier = Modifier.height(MaterialTheme.dimens.medium2))
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

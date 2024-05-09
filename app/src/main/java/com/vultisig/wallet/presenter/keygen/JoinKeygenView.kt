package com.vultisig.wallet.presenter.keygen

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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Alignment.Companion.CenterHorizontally
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.google.zxing.integration.android.IntentIntegrator
import com.vultisig.wallet.R
import com.vultisig.wallet.models.Vault
import com.vultisig.wallet.presenter.common.TopBar
import com.vultisig.wallet.ui.theme.appColor
import com.vultisig.wallet.ui.theme.dimens
import com.vultisig.wallet.ui.theme.menloFamily

@Composable
fun JoinKeygenView(navController: NavHostController, vault: Vault) {
    val viewModel: JoinKeygenViewModel = viewModel()
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
    LaunchedEffect(key1 = viewModel) {
        viewModel.setData(vault)
        val integrator = IntentIntegrator(context as Activity)
        integrator.setBarcodeImageEnabled(true)
        integrator.setOrientationLocked(true)
        integrator.setPrompt("Scan the QR code on your main device")
        scanQrCodeLauncher.launch(integrator.createScanIntent())
    }

    when (viewModel.currentState.value) {
        JoinKeygenState.DiscoveryingSessionID -> {
            DiscoveryingSessionID(navController = navController)
        }

        JoinKeygenState.DiscoverService -> {
            val nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
            viewModel.discoveryMediator(nsdManager)
            DiscoverService(navController = navController)
        }

        JoinKeygenState.JoinKeygen -> {
            LaunchedEffect(key1 = viewModel) {
                viewModel.joinKeygen()
            }
            JoiningKeygen(navController = navController)
        }

        JoinKeygenState.WaitingForKeygenStart -> {
            LaunchedEffect(key1 = viewModel) {
                viewModel.waitForKeygenToStart()
            }
            WaitingForKeygenToStart(navController = navController)
        }

        JoinKeygenState.Keygen -> {
            GeneratingKey(navController = navController, viewModel.generatingKeyViewModel)
        }

        JoinKeygenState.FailedToStart -> {
            KeygenFailedToStart(
                navController = navController,
                errorMessage = viewModel.errorMessage.value
            )
        }

        JoinKeygenState.ERROR -> {
            KeygenFailedToStart(
                navController = navController,
                errorMessage = viewModel.errorMessage.value
            )
        }
    }
}

@Composable
fun DiscoveryingSessionID(navController: NavHostController) {
    Column(
        horizontalAlignment = CenterHorizontally,
        modifier = Modifier
            .background(MaterialTheme.appColor.oxfordBlue800)
            .padding(
                vertical = MaterialTheme.dimens.marginMedium,
                horizontal = MaterialTheme.dimens.marginSmall
            )
    ) {
        TopBar(
            centerText = "Keygen", startIcon = R.drawable.caret_left,
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
                color = MaterialTheme.appColor.neutral0,
                style = MaterialTheme.menloFamily.bodyMedium
            )
            CircularProgressIndicator(
                color = MaterialTheme.appColor.neutral0,
                modifier = Modifier.padding(MaterialTheme.dimens.marginMedium)
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun PreviewDiscoveryingSessionID() {
    val navController = rememberNavController()
    DiscoveryingSessionID(navController = navController)
}

@Composable
fun DiscoverService(navController: NavHostController) {
    Column(
        horizontalAlignment = CenterHorizontally,
        modifier = Modifier
            .background(MaterialTheme.appColor.oxfordBlue800)
            .padding(
                vertical = MaterialTheme.dimens.marginMedium,
                horizontal = MaterialTheme.dimens.marginSmall
            )
    ) {
        TopBar(
            centerText = "Keygen", startIcon = R.drawable.caret_left,
            navController = navController
        )
        Spacer(modifier = Modifier.height(MaterialTheme.dimens.medium2))
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Discovering Service",
                color = MaterialTheme.appColor.neutral0,
                style = MaterialTheme.menloFamily.bodyMedium
            )
            CircularProgressIndicator(
                color = MaterialTheme.appColor.neutral0,
                modifier = Modifier.padding(MaterialTheme.dimens.marginMedium)
            )
        }
    }
}

@Composable
fun JoiningKeygen(navController: NavHostController) {
    Column(
        horizontalAlignment = CenterHorizontally,
        modifier = Modifier
            .background(MaterialTheme.appColor.oxfordBlue800)
            .padding(
                vertical = MaterialTheme.dimens.marginMedium,
                horizontal = MaterialTheme.dimens.marginSmall
            )
    ) {
        TopBar(
            centerText = "Keygen", startIcon = R.drawable.caret_left,
            navController = navController
        )
        Spacer(modifier = Modifier.height(MaterialTheme.dimens.medium2))
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Joining Keygen",
                color = MaterialTheme.appColor.neutral0,
                style = MaterialTheme.menloFamily.bodyMedium
            )
            CircularProgressIndicator(
                color = MaterialTheme.appColor.neutral0,
                modifier = Modifier.padding(MaterialTheme.dimens.marginMedium)
            )
        }
    }
}

@Composable
fun WaitingForKeygenToStart(navController: NavHostController) {
    Column(
        horizontalAlignment = CenterHorizontally,
        modifier = Modifier
            .background(MaterialTheme.appColor.oxfordBlue800)
            .padding(
                vertical = MaterialTheme.dimens.marginMedium,
                horizontal = MaterialTheme.dimens.marginSmall
            )
    ) {
        TopBar(
            centerText = "Keygen", startIcon = R.drawable.caret_left,
            navController = navController
        )
        Spacer(modifier = Modifier.height(MaterialTheme.dimens.medium2))
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Waiting for Keygen to start",
                color = MaterialTheme.appColor.neutral0,
                style = MaterialTheme.menloFamily.bodyMedium
            )
            CircularProgressIndicator(
                color = MaterialTheme.appColor.neutral0,
                modifier = Modifier.padding(MaterialTheme.dimens.marginMedium)
            )
        }
    }
}

@Composable
fun KeygenFailedToStart(navController: NavHostController, errorMessage: String) {
    Column(
        horizontalAlignment = CenterHorizontally,
        modifier = Modifier
            .background(MaterialTheme.appColor.oxfordBlue800)
            .padding(
                vertical = MaterialTheme.dimens.marginMedium,
                horizontal = MaterialTheme.dimens.marginSmall
            )
    ) {
        TopBar(
            centerText = "Keygen", startIcon = R.drawable.caret_left,
            navController = navController
        )
        Spacer(modifier = Modifier.height(MaterialTheme.dimens.medium2))
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Failed to start,error: $errorMessage",
                color = MaterialTheme.appColor.neutral0,
                style = MaterialTheme.menloFamily.bodyMedium
            )
        }
    }
}

package com.voltix.wallet.presenter.keygen

import android.app.Activity
import android.content.Context
import android.net.nsd.NsdManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment.Companion.CenterHorizontally
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.google.zxing.integration.android.IntentIntegrator
import com.voltix.wallet.R
import com.voltix.wallet.app.ui.theme.appColor
import com.voltix.wallet.app.ui.theme.dimens
import com.voltix.wallet.app.ui.theme.menloFamily
import com.voltix.wallet.models.Vault
import com.voltix.wallet.presenter.common.TopBar

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
        when (viewModel.currentState.value) {
            JoinKeygenState.DiscoveryingSessionID -> {
                Column {
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

            JoinKeygenState.DiscoverService -> {
                val nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
                viewModel.discoveryMediator(nsdManager)
                Column {
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

            JoinKeygenState.JOINKeygen -> {
                LaunchedEffect(key1 = viewModel) {
                    viewModel.joinKeygen()
                }
                Column {
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

            JoinKeygenState.WaitingForKeygenStart -> {
                Column {
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
            JoinKeygenState.FailedToStart -> {
                Column {
                    Text(
                        text = "Failed to start,error: ${viewModel.errorMessage.value}",
                        color = MaterialTheme.appColor.neutral0,
                        style = MaterialTheme.menloFamily.bodyMedium
                    )
                }
            }
            JoinKeygenState.ERROR -> {
                Column {
                    Text(
                        text = "Error: ${viewModel.errorMessage.value}",
                        color = MaterialTheme.appColor.neutral0,
                        style = MaterialTheme.menloFamily.bodyMedium
                    )
                }
            }
        }
    }
}
package com.vultisig.wallet.presenter.keygen

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Alignment.Companion.CenterHorizontally
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.vultisig.wallet.R
import com.vultisig.wallet.app.ui.theme.appColor
import com.vultisig.wallet.app.ui.theme.dimens
import com.vultisig.wallet.app.ui.theme.menloFamily
import com.vultisig.wallet.presenter.common.KeepScreenOn
import com.vultisig.wallet.presenter.common.TopBar
import com.vultisig.wallet.presenter.navigation.Screen

@Composable
fun GeneratingKey(navController: NavHostController, viewModel: GeneratingKeyViewModel) {
    KeepScreenOn()
    val context: Context = LocalContext.current.applicationContext
    LaunchedEffect(key1 = Unit) {
        // kick it off to generate key
        viewModel.generateKey()
    }
    val textColor = MaterialTheme.appColor.neutral0
    Column(
        horizontalAlignment = CenterHorizontally,
        modifier = Modifier
            .background(MaterialTheme.appColor.oxfordBlue800)
            .padding(
                vertical = MaterialTheme.dimens.marginMedium,
                horizontal = MaterialTheme.dimens.marginSmall
            )
    ) {
        TopBar(centerText = "Keygen", navController = navController)


        when (viewModel.currentState.value) {
            KeygenState.CreatingInstance -> {
                KeygenIndicator(
                    statusText = "PREPARING VAULT",
                    progress = 0.25F,
                    modifier = Modifier.fillMaxSize()
                )
            }

            KeygenState.KeygenECDSA -> {
                KeygenIndicator(
                    statusText = "GENERATING ECDSA KEY",
                    progress = 0.5F,
                    modifier = Modifier.fillMaxSize()
                )
            }

            KeygenState.KeygenEdDSA -> {
                KeygenIndicator(
                    statusText = "GENERATING EdDSA KEY",
                    progress = 0.7F,
                    modifier = Modifier.fillMaxSize()
                )
            }

            KeygenState.ReshareECDSA -> {
                KeygenIndicator(
                    statusText = "RESHARING ECDSA Key",
                    progress = 0.5F,
                    modifier = Modifier.fillMaxSize()
                )
            }

            KeygenState.ReshareEdDSA -> {
                KeygenIndicator(
                    statusText = "RESHARING EdDSA Key",
                    progress = 0.75F,
                    modifier = Modifier.fillMaxSize()
                )
            }

            KeygenState.Success -> {
                LaunchedEffect(key1 = viewModel) {
                    viewModel.saveVault(context)
                    viewModel.stopService(context)
                    Thread.sleep(2000) // wait for 2 seconds
                    navController.navigate(Screen.Home.route)
                }
                // TODO: play an animation
                KeygenIndicator(
                    statusText = "SUCCESS",
                    progress = 1.0F,
                    modifier = Modifier.fillMaxSize()
                )
            }

            KeygenState.ERROR -> {
                LaunchedEffect(key1 = viewModel) {
                    // stop the service , restart it when user need it
                    viewModel.stopService(context)
                }
                Text(
                    text = "Keygen Failed",
                    color = textColor,
                    style = MaterialTheme.menloFamily.headlineSmall
                )
                Spacer(modifier = Modifier.height(MaterialTheme.dimens.small1))
                Text(
                    text = viewModel.errorMessage.value,
                    color = textColor,
                    style = MaterialTheme.menloFamily.bodyMedium
                )
            }
        }

        Spacer(modifier = Modifier.weight(1.0f))

        Icon(
            painter = painterResource(id = R.drawable.wifi),
            contentDescription = null,
            tint = MaterialTheme.appColor.neutral0
        )
        Spacer(modifier = Modifier.height(MaterialTheme.dimens.small1))
        Text(
            modifier = Modifier.padding(horizontal = MaterialTheme.dimens.large),
            text = "Keep devices on the same WiFi Network with vultisig open.",
            color = textColor,
            style = MaterialTheme.menloFamily.headlineSmall.copy(
                textAlign = TextAlign.Center, fontSize = 13.sp
            ),
        )

        Spacer(modifier = Modifier.height(MaterialTheme.dimens.medium1))
    }
}


@Composable
fun KeygenIndicator(statusText: String, progress: Float, modifier: Modifier) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.fillMaxSize()
    ) {
        Text(
            text = statusText,
            modifier = Modifier.align(Alignment.Center),
            color = MaterialTheme.appColor.neutral0
        )
        Column(
            verticalArrangement = Arrangement.Center,
            modifier = modifier.fillMaxSize()
        ) {

            CircularProgressIndicator(
                progress = { progress },
                strokeWidth = 16.dp,
                color = MaterialTheme.appColor.turquoise600Main,
                trackColor = MaterialTheme.appColor.oxfordBlue600Main,
                modifier = modifier
                    .padding(MaterialTheme.dimens.marginMedium)
                    .fillMaxWidth()
                    .aspectRatio(1f)
            )
        }
    }
}

@Preview
@Composable
fun GeneratingKeyPreview() {
    KeygenIndicator("Generating ECDSA Key", 0.5f, Modifier.fillMaxSize())
}

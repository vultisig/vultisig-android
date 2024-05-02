package com.voltix.wallet.presenter.keygen

import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.voltix.wallet.R
import com.voltix.wallet.app.ui.theme.appColor
import com.voltix.wallet.app.ui.theme.dimens
import com.voltix.wallet.app.ui.theme.menloFamily
import com.voltix.wallet.presenter.common.KeepScreenOn
import com.voltix.wallet.presenter.common.TopBar
import com.voltix.wallet.presenter.navigation.Screen

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

        Spacer(modifier = Modifier.weight(1.0f))
        when (viewModel.currentState.value) {
            KeygenState.CreatingInstance -> {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "Creating TSS Instance",
                        color = textColor,
                        style = MaterialTheme.menloFamily.headlineSmall
                    )
                    CircularProgressIndicator(
                        color = MaterialTheme.appColor.neutral0,
                        modifier = Modifier.padding(MaterialTheme.dimens.marginMedium)
                    )
                }
            }

            KeygenState.KeygenECDSA -> {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "Generating ECDSA Key",
                        color = textColor,
                        style = MaterialTheme.menloFamily.headlineSmall
                    )
                    CircularProgressIndicator(
                        color = MaterialTheme.appColor.neutral0,
                        modifier = Modifier.padding(MaterialTheme.dimens.marginMedium)
                    )
                }
            }

            KeygenState.KeygenEdDSA -> {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "Generating EdDSA key",
                        color = textColor,
                        style = MaterialTheme.menloFamily.headlineSmall
                    )
                    CircularProgressIndicator(
                        color = MaterialTheme.appColor.neutral0,
                        modifier = Modifier.padding(MaterialTheme.dimens.marginMedium)
                    )
                }
            }

            KeygenState.ReshareECDSA -> {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "Reshare ECDSA Key",
                        color = textColor,
                        style = MaterialTheme.menloFamily.headlineSmall
                    )
                    CircularProgressIndicator(
                        color = MaterialTheme.appColor.neutral0,
                        modifier = Modifier.padding(MaterialTheme.dimens.marginMedium)
                    )
                }
            }

            KeygenState.ReshareEdDSA -> {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "Reshare EdDSA Key",
                        color = textColor,
                        style = MaterialTheme.menloFamily.headlineSmall
                    )
                    CircularProgressIndicator(
                        color = MaterialTheme.appColor.neutral0,
                        modifier = Modifier.padding(MaterialTheme.dimens.marginMedium)
                    )
                }
            }

            KeygenState.Success -> {
                LaunchedEffect(key1 = viewModel) {
                    viewModel.saveVault(context)
                    viewModel.stopService(context)
                    Thread.sleep(2000) // wait for 2 seconds
                    navController.navigate(Screen.Home.route)
                }
                Text(
                    text = "Keygen Success",
                    color = textColor,
                    style = MaterialTheme.menloFamily.headlineSmall
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

        Spacer(modifier = Modifier.height(MaterialTheme.dimens.small1))
        Image(
            painterResource(id = R.drawable.generating),
            contentDescription = null,
            modifier = Modifier.size(100.dp)
        )

        Spacer(modifier = Modifier.weight(1.0f))

        Icon(
            painter = painterResource(id = R.drawable.wifi),
            contentDescription = null,
            tint = MaterialTheme.appColor.neutral0
        )
        Spacer(modifier = Modifier.height(MaterialTheme.dimens.small1))
        Text(
            modifier = Modifier.padding(horizontal = MaterialTheme.dimens.large),
            text = "Keep devices on the same WiFi Network with VOLTIX open.",
            color = textColor,
            style = MaterialTheme.menloFamily.headlineSmall.copy(
                textAlign = TextAlign.Center, fontSize = 13.sp
            ),
        )

        Spacer(modifier = Modifier.height(MaterialTheme.dimens.medium1))
    }
}

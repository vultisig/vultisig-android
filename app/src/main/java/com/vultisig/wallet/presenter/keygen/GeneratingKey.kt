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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.google.gson.Gson
import com.vultisig.wallet.R
import com.vultisig.wallet.data.on_board.db.VaultDB
import com.vultisig.wallet.models.TssAction
import com.vultisig.wallet.models.Vault
import com.vultisig.wallet.presenter.common.KeepScreenOn
import com.vultisig.wallet.ui.components.TopBar
import com.vultisig.wallet.ui.navigation.Screen
import com.vultisig.wallet.ui.theme.Theme
import com.vultisig.wallet.ui.theme.dimens

@Composable
fun GeneratingKey(navController: NavHostController, viewModel: GeneratingKeyViewModel) {
    KeepScreenOn()
    val context: Context = LocalContext.current.applicationContext
    LaunchedEffect(key1 = Unit) {
        // kick it off to generate key
        viewModel.generateKey()
    }
    val textColor = Theme.colors.neutral0
    Column(
        horizontalAlignment = CenterHorizontally,
        modifier = Modifier
            .background(Theme.colors.oxfordBlue800)
            .padding(
                vertical = MaterialTheme.dimens.marginMedium,
                horizontal = MaterialTheme.dimens.marginSmall
            )
    ) {
        TopBar(
            centerText = stringResource(R.string.generating_key_title),
            navController = navController
        )
        Spacer(modifier = Modifier.weight(1f))
        when (viewModel.currentState.value) {
            KeygenState.CreatingInstance -> {
                KeygenIndicator(
                    statusText = stringResource(R.string.generating_key_preparing_vault),
                    progress = 0.25F,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            KeygenState.KeygenECDSA -> {
                KeygenIndicator(
                    statusText = stringResource(R.string.generating_key_screen_generating_ecdsa_key),
                    progress = 0.5F,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            KeygenState.KeygenEdDSA -> {
                KeygenIndicator(
                    statusText = stringResource(R.string.generating_key_screen_generating_eddsa_key),
                    progress = 0.7F,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            KeygenState.ReshareECDSA -> {
                KeygenIndicator(
                    statusText = stringResource(R.string.generating_key_screen_resharing_ecdsa_key),
                    progress = 0.5F,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            KeygenState.ReshareEdDSA -> {
                KeygenIndicator(
                    statusText = stringResource(R.string.generating_key_screen_resharing_eddsa_key),
                    progress = 0.75F,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            KeygenState.Success -> {
                LaunchedEffect(key1 = viewModel) {
                    viewModel.saveVault()
                    Thread.sleep(2000) // wait for 2 seconds
                    viewModel.stopService(context)
                    navController.navigate(Screen.Home.route)
                }
                // TODO: play an animation
                KeygenIndicator(
                    statusText = stringResource(R.string.generating_key_screen_success),
                    progress = 1.0F,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            KeygenState.ERROR -> {
                LaunchedEffect(key1 = viewModel) {
                    // stop the service , restart it when user need it
                    viewModel.stopService(context)
                }
                Text(
                    text = stringResource(R.string.generating_key_screen_keygen_failed),
                    color = textColor,
                    style = Theme.menlo.headlineSmall
                )
                Spacer(modifier = Modifier.height(MaterialTheme.dimens.small1))
                Text(
                    text = viewModel.errorMessage.value,
                    color = textColor,
                    style = Theme.menlo.bodyMedium
                )
            }
        }
        Spacer(modifier = Modifier.weight(1f))
        Icon(
            painter = painterResource(id = R.drawable.wifi),
            contentDescription = null,
            tint = Theme.colors.neutral0
        )
        Spacer(modifier = Modifier.height(MaterialTheme.dimens.small1))
        Text(
            modifier = Modifier.padding(horizontal = MaterialTheme.dimens.large),
            text = stringResource(R.string.generating_key_screen_keep_devices_on_the_same_wifi_network_with_vultisig_open),
            color = textColor,
            style = Theme.menlo.headlineSmall.copy(
                textAlign = TextAlign.Center, fontSize = 13.sp
            ),
        )

        Spacer(modifier = Modifier.height(MaterialTheme.dimens.medium1))
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF121212)
@Composable
fun PreviewGeneratingKey() {
    val navController = rememberNavController()
    val viewModelForReview = GeneratingKeyViewModel(
        vault = Vault("new Vault"),
        action = TssAction.KEYGEN,
        keygenCommittee = listOf(""),
        oldCommittee = listOf(""),
        serverAddress = "http://127.0.0.1:18080",
        sessionId = "123456",
        encryptionKeyHex = "",
        oldResharePrefix = "",
        gson = Gson(),
        vaultDB = VaultDB(LocalContext.current)
    )
    GeneratingKey(navController, viewModelForReview)
}

@Composable
fun KeygenIndicator(statusText: String, progress: Float, modifier: Modifier) {
    Box(
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = statusText,
            modifier = Modifier.align(Alignment.Center),
            color = Theme.colors.neutral0
        )
        Column(
            verticalArrangement = Arrangement.Center,
            modifier = modifier.fillMaxWidth()
        ) {

            CircularProgressIndicator(
                progress = { progress },
                strokeWidth = 16.dp,
                color = Theme.colors.turquoise600Main,
                trackColor = Theme.colors.oxfordBlue600Main,
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
fun KeygenIndicatorPreview() {
    KeygenIndicator("Generating ECDSA Key", 0.5f, Modifier.fillMaxSize())
}

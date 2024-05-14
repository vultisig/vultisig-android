package com.vultisig.wallet.presenter.keysign

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.vultisig.wallet.R
import com.vultisig.wallet.presenter.common.KeepScreenOn
import com.vultisig.wallet.ui.components.TopBar
import com.vultisig.wallet.ui.theme.Theme
import com.vultisig.wallet.ui.theme.dimens

@Composable
internal fun Keysign(navController: NavController, viewModel: KeysignViewModel) {
    KeepScreenOn()
    val context: Context = LocalContext.current.applicationContext
    LaunchedEffect(key1 = Unit) {
        // kick it off to generate key
        viewModel.startKeysign()
    }
    val textColor = Theme.colors.neutral0
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
            centerText = stringResource(id = R.string.keysign),
            navController = navController,
            startIcon = R.drawable.caret_left,
        )
        Spacer(modifier = Modifier.weight(1f))

        when (viewModel.currentState.value) {
            KeysignState.CreatingInstance -> {
                Text(
                    modifier = Modifier.padding(top = MaterialTheme.dimens.medium1),
                    text = "PREPARING VAULT...",
                    style = Theme.menlo.titleLarge,
                    color = Theme.colors.neutral0,
                    textAlign = TextAlign.Center
                )
            }

            KeysignState.KeysignECDSA -> {
                Text(
                    modifier = Modifier.padding(top = MaterialTheme.dimens.medium1),
                    text = "Signing with ECDSA...",
                    style = Theme.menlo.titleLarge,
                    color = Theme.colors.neutral0,
                    textAlign = TextAlign.Center
                )
            }

            KeysignState.KeysignEdDSA -> {
                Text(
                    modifier = Modifier.padding(top = MaterialTheme.dimens.medium1),
                    text = "Signing with EdDSA...",
                    style = Theme.menlo.titleLarge,
                    color = Theme.colors.neutral0,
                    textAlign = TextAlign.Center
                )
            }

            KeysignState.KeysignFinished -> {
                Text(
                    modifier = Modifier.padding(top = MaterialTheme.dimens.medium1),
                    text = "Keysign Finished!",
                    style = Theme.menlo.titleLarge,
                    color = Theme.colors.neutral0,
                    textAlign = TextAlign.Center
                )
            }

            KeysignState.ERROR -> {
                LaunchedEffect(key1 = Unit) {

                }
                Text(
                    modifier = Modifier.padding(top = MaterialTheme.dimens.medium1),
                    text = "Error! Please try again. $viewModel.errorMessage.value",
                    style = Theme.menlo.titleLarge,
                    color = Theme.colors.neutral0,
                    textAlign = TextAlign.Center
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
            text = "Keep devices on the same WiFi Network with vultisig open.",
            color = textColor,
            style = Theme.menlo.headlineSmall.copy(
                textAlign = TextAlign.Center, fontSize = 13.sp
            ),
        )

        Spacer(modifier = Modifier.height(MaterialTheme.dimens.medium1))
    }
}

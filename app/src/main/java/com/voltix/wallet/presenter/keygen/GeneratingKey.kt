package com.voltix.wallet.presenter.keygen

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment.Companion.CenterHorizontally
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.voltix.wallet.R
import com.voltix.wallet.app.ui.theme.appColor
import com.voltix.wallet.app.ui.theme.dimens
import com.voltix.wallet.app.ui.theme.menloFamily
import com.voltix.wallet.presenter.common.TopBar

@Composable
fun GeneratingKey(navController: NavHostController, viewModel: GeneratingKeyViewModel) {
    LaunchedEffect(key1 = viewModel) {
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
        when(viewModel.currentState.value){
            KeygenState.CreatingInstance -> {
                Text(
                    text = "Creating TSS Instance",
                    color = textColor,
                    style = MaterialTheme.menloFamily.headlineSmall
                )
            }
            KeygenState.KeygenECDSA -> {
                Text(
                    text = "Generating ECDSA Key",
                    color = textColor,
                    style = MaterialTheme.menloFamily.headlineSmall
                )
            }
            KeygenState.KeygenEdDSA -> {
                Text(
                    text = "Generateing EDDSA key",
                    color = textColor,
                    style = MaterialTheme.menloFamily.headlineSmall
                )
            }
            KeygenState.ReshareECDSA -> {
                Text(
                    text = "Reshare ECDSA Key",
                    color = textColor,
                    style = MaterialTheme.menloFamily.headlineSmall
                )
            }
            KeygenState.ReshareEdDSA -> {
                Text(
                    text = "Reshare EdDSA Key",
                    color = textColor,
                    style = MaterialTheme.menloFamily.headlineSmall
                )
            }
            KeygenState.Success -> {
                // TODO: add animation
                Text(
                    text = "Keygen Success",
                    color = textColor,
                    style = MaterialTheme.menloFamily.headlineSmall
                )
            }
            KeygenState.ERROR -> {
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

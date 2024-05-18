package com.vultisig.wallet.presenter.keygen

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment.Companion.CenterHorizontally
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.vultisig.wallet.R
import com.vultisig.wallet.R.drawable
import com.vultisig.wallet.ui.components.MultiColorButton
import com.vultisig.wallet.ui.components.TopBar
import com.vultisig.wallet.ui.navigation.Screen
import com.vultisig.wallet.ui.theme.Theme
import com.vultisig.wallet.ui.theme.dimens

@Composable
fun Setup(navController: NavHostController) {
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
            centerText = stringResource(R.string.setup_title),
            startIcon = drawable.caret_left,
            endIcon = drawable.question,
            navController = navController
        )
        Spacer(modifier = Modifier.height(MaterialTheme.dimens.medium2))
        val current = 2
        val total = 3
        Text(
            text = stringResource(R.string.setup_device_of_vault, current, total),
            color = textColor,
            style = Theme.montserrat.bodyLarge
        )
        Spacer(modifier = Modifier.height(MaterialTheme.dimens.small2))

        Text(
            text = stringResource(R.string.setup_any_total_devices, total),
            color = textColor,
            style = Theme.montserrat.bodyMedium
        )
        Spacer(modifier = Modifier.weight(1.0f))

        Image(
            painter = painterResource(id = drawable.devices),
            contentDescription = "devices",
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1.6f)
        )
        Spacer(modifier = Modifier.height(MaterialTheme.dimens.small1))

        Text(
            style = Theme.montserrat.body3,
            text = stringResource(R.string.setup_devices_to_create_a_vault, total),
            color = textColor
        )
        Text(
            style = Theme.montserrat.body3,
            text = stringResource(R.string.setup_devices_to_sign_a_transaction, current),
            color = textColor
        )
        Text(
            style = Theme.montserrat.body3,
            text = stringResource(R.string.setup_automatically_backed_up),
            color = textColor
        )

        Spacer(modifier = Modifier.weight(1.0f))

        Image(painter = painterResource(id = drawable.wifi), contentDescription = null)
        Spacer(modifier = Modifier.height(MaterialTheme.dimens.small1))
        Text(
            modifier = Modifier.padding(horizontal = MaterialTheme.dimens.large),
            text = stringResource(R.string.setup_keep_devices_on_the_same_wifi_network_with_vultisig_open),
            color = textColor,
            style = Theme.menlo.headlineSmall.copy(
                textAlign = TextAlign.Center, fontSize = 13.sp
            ),
        )
        Spacer(modifier = Modifier.height(MaterialTheme.dimens.small2))
        MultiColorButton(
            text = stringResource(R.string.setup_start),
            backgroundColor = Theme.colors.turquoise600Main,
            textColor = Theme.colors.oxfordBlue600Main,
            minHeight = MaterialTheme.dimens.minHeightButton,
            textStyle = Theme.montserrat.titleLarge,
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = MaterialTheme.dimens.marginMedium,
                    end = MaterialTheme.dimens.marginMedium,
                    bottom = MaterialTheme.dimens.marginMedium,
                )
        ) {
            navController.navigate(Screen.KeygenFlow.createRoute(Screen.KeygenFlow.DEFAULT_NEW_VAULT))
        }

        MultiColorButton(
            text = stringResource(R.string.setup_join),
            backgroundColor = Theme.colors.oxfordBlue600Main,
            textColor = Theme.colors.turquoise600Main,
            iconColor = Theme.colors.oxfordBlue600Main,
            borderSize = 1.dp,
            minHeight = MaterialTheme.dimens.minHeightButton,
            textStyle = Theme.montserrat.titleLarge,
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = MaterialTheme.dimens.marginMedium,
                    end = MaterialTheme.dimens.marginMedium,
                    bottom = MaterialTheme.dimens.buttonMargin,
                )
        ) {
            navController.navigate(Screen.JoinKeygen.route)
        }
    }
}

@Preview(showBackground = true)
@Composable
fun SetupPreview() {
    val navController = rememberNavController()
    Setup(navController)

}
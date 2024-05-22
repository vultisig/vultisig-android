package com.vultisig.wallet.presenter.keygen

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment.Companion.CenterHorizontally
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.vultisig.wallet.R
import com.vultisig.wallet.R.drawable
import com.vultisig.wallet.ui.components.DevicesOnSameNetworkHint
import com.vultisig.wallet.ui.components.MultiColorButton
import com.vultisig.wallet.ui.components.UiBarContainer
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.navigation.Screen
import com.vultisig.wallet.ui.theme.Theme
import com.vultisig.wallet.ui.theme.dimens

@Composable
internal fun Setup(
    navController: NavHostController
) {
    val uriHandler = LocalUriHandler.current
    val helpLink = stringResource(R.string.link_docs_create_vault)

    val textColor = Theme.colors.neutral0

    UiBarContainer(
        navController = navController,
        title = stringResource(R.string.setup_title),
        endIcon = drawable.question,
        onEndIconClick = {
            uriHandler.openUri(helpLink)
        },
    ) {
        Column(
            horizontalAlignment = CenterHorizontally,
        ) {
            UiSpacer(size = 16.dp)

            val current = 2
            val total = 3

            Text(
                text = stringResource(R.string.setup_device_of_vault, current, total),
                color = textColor,
                style = Theme.montserrat.body3,
                textAlign = TextAlign.Center
            )

            Image(
                painter = painterResource(id = drawable.devices),
                contentDescription = "devices",
                contentScale = ContentScale.FillWidth,
                modifier = Modifier.weight(1f)
            )

            DevicesOnSameNetworkHint(
                title = stringResource(R.string.setup_keep_devices_on_the_same_wifi_network_with_vultisig_open)
            )

            UiSpacer(size = 24.dp)

            MultiColorButton(
                text = stringResource(R.string.setup_start),
                backgroundColor = Theme.colors.turquoise600Main,
                textColor = Theme.colors.oxfordBlue600Main,
                minHeight = MaterialTheme.dimens.minHeightButton,
                textStyle = Theme.montserrat.subtitle1,
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
                textStyle = Theme.montserrat.subtitle1,
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
}

@Preview(showBackground = true)
@Composable
private fun SetupPreview() {
    val navController = rememberNavController()
    Setup(navController)
}
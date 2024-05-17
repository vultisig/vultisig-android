package com.vultisig.wallet.ui.screens.keysign

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.MultiColorButton
import com.vultisig.wallet.ui.components.UiBarContainer
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.theme.Theme
import com.vultisig.wallet.ui.theme.dimens


@Composable
internal fun KeysignErrorScreen(
    navController: NavController,
    errorMessage: String = "",
) {
    UiBarContainer(
        navController = navController,
        title = stringResource(R.string.keysign)
    ) {
        KeysignErrorView(
            navController = navController,
            errorMessage = errorMessage
        )
    }
}

@Composable
internal fun KeysignErrorView(
    navController: NavController,
    errorMessage: String = "",
) {
    Column {
        UiSpacer(weight = 1f)

        Column(
            Modifier
                .padding(
                    start = 81.dp,
                    end = 81.dp,
                ),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Image(
                painter = painterResource(id = R.drawable.danger),
                contentDescription = stringResource(R.string.danger_icon),
                alignment = Alignment.Center
            )
            Text(
                modifier = Modifier.padding(top = MaterialTheme.dimens.medium1),
                text = stringResource(R.string.signing_error_please_try_again_s, errorMessage),
                style = Theme.menlo.subtitle1,
                color = Theme.colors.neutral0,
                textAlign = TextAlign.Center
            )
        }

        UiSpacer(weight = 1f)

        Column {
            Text(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally),
                text = stringResource(R.string.bottom_warning_msg_keygen_error_screen),
                style = Theme.menlo.body1,
                color = Theme.colors.neutral0,
                textAlign = TextAlign.Center
            )
            UiSpacer(size = 8.dp)

            MultiColorButton(
                text = stringResource(R.string.try_again),
                minHeight = MaterialTheme.dimens.minHeightButton,
                backgroundColor = Theme.colors.turquoise800,
                textColor = Theme.colors.oxfordBlue800,
                iconColor = Theme.colors.turquoise800,
                textStyle = Theme.montserrat.subtitle1,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(all = 16.dp)
            ) {
                navController.popBackStack()
            }
        }
    }
}

@Preview(showBackground = true, name = "KeysignErrorScreen Preview")
@Composable
private fun PreviewKeysignError() {
    KeysignErrorView(navController = rememberNavController())
}
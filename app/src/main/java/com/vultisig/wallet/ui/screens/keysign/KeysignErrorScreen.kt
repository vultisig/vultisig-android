package com.vultisig.wallet.ui.screens.keysign

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import com.vultisig.wallet.ui.components.InformationNote
import com.vultisig.wallet.ui.components.MultiColorButton
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.theme.Theme


@Composable
internal fun KeysignErrorScreen(
    navController: NavController,
    errorMessage: String = "",
    onTryAgain: () -> Unit,
) {
    KeysignErrorView(
        navController = navController,
        errorMessage = errorMessage,
        onTryAgain = onTryAgain,
    )
}

@Composable
internal fun KeysignErrorView(
    navController: NavController,
    errorMessage: String = "",
    onTryAgain: () -> Unit,
) {
    Column(Modifier.background(Theme.colors.oxfordBlue800)) {
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
                modifier = Modifier.padding(top = 9.dp),
                text = stringResource(R.string.signing_error_please_try_again_s, errorMessage),
                style = Theme.menlo.subtitle1,
                color = Theme.colors.neutral0,
                textAlign = TextAlign.Center
            )
        }

        UiSpacer(weight = 1f)

        Column {
            InformationNote(
                modifier = Modifier.padding(horizontal = 8.dp),
                text = stringResource(R.string.bottom_warning_msg_keygen_error_screen),
            )

            MultiColorButton(
                text = stringResource(R.string.try_again),
                minHeight = 44.dp,
                backgroundColor = Theme.colors.turquoise800,
                textColor = Theme.colors.oxfordBlue800,
                iconColor = Theme.colors.turquoise800,
                textStyle = Theme.montserrat.subtitle1,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(all = 16.dp)
            ) {
                onTryAgain()
            }
        }
    }
}

@Preview(showBackground = true, name = "KeysignErrorScreen Preview")
@Composable
private fun PreviewKeysignError() {

    KeysignErrorView(navController = rememberNavController()){}
}
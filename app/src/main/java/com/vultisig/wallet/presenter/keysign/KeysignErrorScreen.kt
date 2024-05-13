package com.vultisig.wallet.presenter.keysign

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import com.vultisig.wallet.ui.components.TopBar
import com.vultisig.wallet.ui.theme.Theme
import com.vultisig.wallet.ui.theme.dimens


@Composable
fun KeysignErrorScreen(navController: NavController) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Theme.colors.oxfordBlue800)
    ) {

        Spacer(modifier = Modifier.height(MaterialTheme.dimens.small2))

        TopBar(
            modifier = Modifier.align(Alignment.TopCenter),
            centerText = stringResource(R.string.keysign),
            startIcon = R.drawable.caret_left,
            navController = navController
        )

        Column(
            Modifier
                .align(Alignment.Center)
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
                text = stringResource(R.string.signing_error_please_try_again),
                style = Theme.menlo.titleLarge,
                color = Theme.colors.neutral0,
                textAlign = TextAlign.Center
            )
        }

        Column(
            Modifier
                .align(Alignment.BottomCenter)
        ) {
            Text(
                modifier = Modifier
                    .padding(top = 150.dp)
                    .align(Alignment.CenterHorizontally),
                text = stringResource(R.string.bottom_warning_msg_keygen_error_screen),
                style = Theme.menlo.labelMedium,
                color = Theme.colors.neutral0,
                textAlign = TextAlign.Center
            )

            MultiColorButton(
                text = stringResource(R.string.try_again),
                minHeight = MaterialTheme.dimens.minHeightButton,
                backgroundColor = Theme.colors.turquoise800,
                textColor = Theme.colors.oxfordBlue800,
                iconColor = Theme.colors.turquoise800,
                textStyle = Theme.montserrat.titleLarge,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        top = MaterialTheme.dimens.small3,
                        start = MaterialTheme.dimens.small2,
                        end = MaterialTheme.dimens.small2,
                        bottom = MaterialTheme.dimens.small2
                    )
            ) {
                navController.popBackStack()
            }
        }
    }
}

@Preview(showBackground = true, name = "KeysignErrorScreen Preview")
@Composable
fun PreviewKeysignError() {
    KeysignErrorScreen(navController = rememberNavController())
}
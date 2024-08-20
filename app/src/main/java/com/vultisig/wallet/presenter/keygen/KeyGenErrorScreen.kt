package com.vultisig.wallet.presenter.keygen

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import com.vultisig.wallet.ui.components.TopBar
import com.vultisig.wallet.ui.navigation.Screen
import com.vultisig.wallet.ui.theme.Theme

@Composable
fun KeyGenErrorScreen(
    navController: NavController,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Theme.colors.oxfordBlue800)
    ) {

        Spacer(modifier = Modifier.height(15.dp))

        TopBar(
            modifier = Modifier.align(Alignment.TopCenter),
            centerText = stringResource(R.string.keygen),
            navController = rememberNavController()
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
                modifier = Modifier.padding(top = 30.dp),
                text = stringResource(R.string.signing_error_please_try_again),
                style = Theme.menlo.subtitle1,
                color = Theme.colors.neutral0,
                textAlign = TextAlign.Center
            )
        }

        Column(
            Modifier
                .align(Alignment.BottomCenter)
        ) {
            InformationNote(
                modifier = Modifier.padding(horizontal = 8.dp),
                text = stringResource(R.string.bottom_warning_msg_keygen_error_screen),
            )

            MultiColorButton(
                text = stringResource(R.string.try_again),
                minHeight = 45.dp,
                backgroundColor = Theme.colors.turquoise800,
                textColor = Theme.colors.oxfordBlue800,
                iconColor = Theme.colors.turquoise800,
                textStyle = Theme.montserrat.subtitle1,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        vertical = 16.dp,
                        horizontal = 16.dp,
                    ),
            ) {
                navController.navigate(Screen.CreateNewVault.route)
            }
        }
    }
}


@Preview
@Composable
fun KeyGenErrorScreenPreview() {
    KeyGenErrorScreen(
        navController = rememberNavController()
    )
}

package com.vultisig.wallet.ui.screens.reshare

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.GradientInfoCard
import com.vultisig.wallet.ui.components.MultiColorButton
import com.vultisig.wallet.ui.components.vultiGradient
import com.vultisig.wallet.ui.models.keygen.VaultSetupType
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.theme.Theme
import com.vultisig.wallet.ui.theme.Theme.colors

@Composable
internal fun ReshareStartScreen(
    navController: NavHostController,
    vaultId: String,
) {
    Scaffold(
        containerColor = colors.oxfordBlue800,
        content = {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 20.dp),
                verticalArrangement = Arrangement.Top,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Image(
                    painter = painterResource(id = R.drawable.vultisig_icon_text),
                    contentDescription = "Resahre Image"
                )
            }
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(it)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = stringResource(id = R.string.reshare_start_screen_title),
                    color = colors.neutral0,
                    style = Theme.montserrat.heading4,
                    textAlign = TextAlign.Center
                )
                Text(
                    modifier = Modifier.padding(top = 16.dp),
                    text = stringResource(id = R.string.reshare_start_screen_body),
                    color = colors.neutral0,
                    style = Theme.montserrat.body1,
                    textAlign = TextAlign.Center
                )

            }
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .imePadding()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.Bottom,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                GradientInfoCard(
                    stringResource(id = R.string.reshare_start_screen_warning),
                    Brush.vultiGradient()
                )

                MultiColorButton(
                    backgroundColor = colors.turquoise800,
                    textColor = colors.oxfordBlue800,
                    iconColor = colors.turquoise800,
                    textStyle = Theme.montserrat.subtitle1,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            bottom = 16.dp,
                        ),
                    text = stringResource(R.string.reshare_start_screen_start_reshare),
                    onClick = {
                        navController.navigate(
                            Destination.KeygenFlow(
                                vaultName = vaultId,
                                vaultSetupType = VaultSetupType.SECURE,
                                isReshare = true,
                                email = null,
                                password = null,
                            ).route
                        )
                    },
                )
            }
        })

}

@Preview
@Composable
private fun PreviewReshareScreen() {
    ReshareStartScreen(
        rememberNavController(),
        ""
    )
}


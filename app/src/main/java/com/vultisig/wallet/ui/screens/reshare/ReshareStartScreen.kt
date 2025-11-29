package com.vultisig.wallet.ui.screens.reshare

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.GradientInfoCard
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.buttons.VsButton
import com.vultisig.wallet.ui.components.buttons.VsButtonVariant
import com.vultisig.wallet.ui.components.clickOnce
import com.vultisig.wallet.ui.components.vultiGradient
import com.vultisig.wallet.ui.models.reshare.ReshareStartViewModel
import com.vultisig.wallet.ui.theme.Theme
import com.vultisig.wallet.ui.theme.v2.V2.colors

@Composable
internal fun ReshareStartScreen(
    navController: NavController,
    model: ReshareStartViewModel = hiltViewModel(),
) {

    ReshareStartScreen(
        onStartClick = model::start,
        onJoinClick = model::join,
        navController = navController,
    )
}

@Composable
private fun ReshareStartScreen(
    navController: NavController,
    onStartClick: () -> Unit,
    onJoinClick: () -> Unit,
) {
    Scaffold(
        containerColor = colors.backgrounds.primary,
        content = {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 20.dp),
                verticalArrangement = Arrangement.Top,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    IconButton(
                        modifier = Modifier.align(Alignment.CenterStart),
                        onClick = clickOnce(navController::popBackStack)
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_caret_left),
                            contentDescription = null,
                            tint = colors.neutrals.n50,
                        )
                    }
                    Image(
                        modifier = Modifier.align(Alignment.Center),
                        painter = painterResource(id = R.drawable.vultisig_icon_text),
                        contentDescription = "Reshare Image"
                    )
                }
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
                    color = colors.neutrals.n50,
                    style = Theme.montserrat.heading4,
                    textAlign = TextAlign.Center
                )
                Text(
                    modifier = Modifier.padding(top = 16.dp),
                    text = stringResource(id = R.string.reshare_start_screen_body),
                    color = colors.neutrals.n50,
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

                VsButton(
                    label = stringResource(R.string.reshare_start_screen_start_reshare),
                    onClick = onStartClick,
                    modifier = Modifier.fillMaxWidth(),
                )

                UiSpacer(size = 12.dp)

                /* fast&active vaults are temporarily disabled
                MultiColorButton(
                    text = stringResource(R.string.reshare_start_start_with_vultisigner_button),
                    backgroundColor = colors.backgrounds.primary,
                    textColor = colors.turquoise800,
                    iconColor = colors.backgrounds.primary,
                    borderSize = 1.dp,
                    textStyle = Theme.montserrat.subtitle1,
                    modifier = Modifier
                        .fillMaxWidth(),
                    onClick = onStartWithServerClick,
                )

                UiSpacer(size = 12.dp)
                 */

                VsButton(
                    label = stringResource(R.string.reshare_start_join_reshare_button),
                    variant = VsButtonVariant.Secondary,
                    onClick = onJoinClick,
                    modifier = Modifier.fillMaxWidth(),
                )

                UiSpacer(size = 16.dp)
            }
        })

}

@Preview
@Composable
private fun PreviewReshareScreen() {
    ReshareStartScreen(
        onStartClick = {},
        onJoinClick = {},
        navController = rememberNavController(),
    )
}


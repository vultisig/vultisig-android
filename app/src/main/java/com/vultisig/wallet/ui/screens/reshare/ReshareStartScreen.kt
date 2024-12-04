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
import com.vultisig.wallet.ui.components.MultiColorButton
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.clickOnce
import com.vultisig.wallet.ui.components.vultiGradient
import com.vultisig.wallet.ui.models.reshare.ReshareStartViewModel
import com.vultisig.wallet.ui.theme.Theme
import com.vultisig.wallet.ui.theme.Theme.colors

@Composable
internal fun ReshareStartScreen(
    navController: NavController,
    model: ReshareStartViewModel = hiltViewModel(),
) {

    ReshareStartScreen(
        onStartClick = model::start,
        onStartWithServerClick = model::startWithServer,
        onJoinClick = model::join,
        navController = navController,
    )
}

@Composable
private fun ReshareStartScreen(
    navController: NavController,
    onStartClick: () -> Unit,
    onStartWithServerClick: () -> Unit,
    onJoinClick: () -> Unit,
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
                Box(modifier = Modifier.fillMaxWidth()) {
                    IconButton(
                        modifier = Modifier.align(Alignment.CenterStart),
                        onClick = clickOnce(navController::popBackStack)
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_caret_left),
                            contentDescription = null,
                            tint = colors.neutral0,
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
                        .fillMaxWidth(),
                    text = stringResource(R.string.reshare_start_screen_start_reshare),
                    onClick = onStartClick,
                )

                UiSpacer(size = 12.dp)

                /* fast&active vaults are temporarily disabled
                MultiColorButton(
                    text = stringResource(R.string.reshare_start_start_with_vultisigner_button),
                    backgroundColor = colors.oxfordBlue800,
                    textColor = colors.turquoise800,
                    iconColor = colors.oxfordBlue800,
                    borderSize = 1.dp,
                    textStyle = Theme.montserrat.subtitle1,
                    modifier = Modifier
                        .fillMaxWidth(),
                    onClick = onStartWithServerClick,
                )

                UiSpacer(size = 12.dp)
                 */

                MultiColorButton(
                    text = stringResource(R.string.reshare_start_join_reshare_button),
                    backgroundColor = colors.oxfordBlue800,
                    textColor = colors.turquoise800,
                    iconColor = colors.oxfordBlue800,
                    borderSize = 1.dp,
                    textStyle = Theme.montserrat.subtitle1,
                    modifier = Modifier
                        .fillMaxWidth(),
                    onClick = onJoinClick,
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
        onStartWithServerClick = {},
        onJoinClick = {},
        navController = rememberNavController(),
    )
}


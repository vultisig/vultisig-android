package com.vultisig.wallet.ui.screens.reshare

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.GradientInfoCard
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.buttons.VsButton
import com.vultisig.wallet.ui.components.buttons.VsButtonVariant
import com.vultisig.wallet.ui.components.v2.scaffold.V2Scaffold
import com.vultisig.wallet.ui.components.vultiGradient
import com.vultisig.wallet.ui.models.reshare.ReshareStartViewModel
import com.vultisig.wallet.ui.theme.Theme

@Composable
internal fun ReshareStartScreen(model: ReshareStartViewModel = hiltViewModel()) {

    ReshareStartScreen(
        onBackClick = model::back,
        onStartClick = model::start,
        onJoinClick = model::join,
    )
}

@Composable
private fun ReshareStartScreen(
    onBackClick: () -> Unit,
    onStartClick: () -> Unit,
    onJoinClick: () -> Unit,
) {
    V2Scaffold(
        title = stringResource(id = R.string.reshare_start_screen_title),
        onBackClick = onBackClick,
        content = {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = stringResource(id = R.string.reshare_start_screen_title),
                    color = Theme.v2.colors.text.primary,
                    style = Theme.brockmann.headings.title2,
                    textAlign = TextAlign.Center,
                )
                Text(
                    modifier = Modifier.padding(top = 16.dp),
                    text = stringResource(id = R.string.reshare_start_screen_body),
                    color = Theme.v2.colors.text.primary,
                    style = Theme.brockmann.body.s.medium,
                    textAlign = TextAlign.Center,
                )
            }
            Column(
                modifier = Modifier.fillMaxSize().imePadding(),
                verticalArrangement = Arrangement.Bottom,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                GradientInfoCard(
                    stringResource(id = R.string.reshare_start_screen_warning),
                    Brush.vultiGradient(),
                )

                VsButton(
                    label = stringResource(R.string.reshare_start_screen_start_reshare),
                    onClick = onStartClick,
                    modifier = Modifier.fillMaxWidth(),
                )

                UiSpacer(size = 12.dp)

                VsButton(
                    label = stringResource(R.string.reshare_start_join_reshare_button),
                    variant = VsButtonVariant.Secondary,
                    onClick = onJoinClick,
                    modifier = Modifier.fillMaxWidth(),
                )

                UiSpacer(size = 16.dp)
            }
        },
    )
}

@Preview
@Composable
private fun PreviewReshareScreen() {
    ReshareStartScreen(onBackClick = {}, onStartClick = {}, onJoinClick = {})
}

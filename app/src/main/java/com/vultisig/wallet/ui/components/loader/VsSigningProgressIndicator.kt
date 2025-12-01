package com.vultisig.wallet.ui.components.loader

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.AppVersionText
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.rive.RiveAnimation
import com.vultisig.wallet.ui.theme.Theme

@Composable
fun VsSigningProgressIndicator(
    text: String,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                color = Theme.v2.colors.backgrounds.primary,
            )
            .padding(
                horizontal = 16.dp,
                vertical = 24.dp,
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        UiSpacer(weight = 1f)

        RiveAnimation(
            animation = R.raw.riv_connecting_with_server,
            modifier = Modifier
                .size(24.dp)
        )

        UiSpacer(16.dp)

        Text(
            text = text,
            color = Theme.v2.colors.text.primary,
            style = Theme.brockmann.headings.title2,
            textAlign = TextAlign.Center,
        )

        UiSpacer(weight = 1f)

        UiSpacer(size = 60.dp)

        AppVersionText()
    }
}

@Preview
@Composable
private fun VsSigningProgressIndicatorPreview() {
    VsSigningProgressIndicator(
        text = "Signing transaction...",
    )
}
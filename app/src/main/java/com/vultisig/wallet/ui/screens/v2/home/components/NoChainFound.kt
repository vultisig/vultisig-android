package com.vultisig.wallet.ui.screens.v2.home.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.buttons.VsButton
import com.vultisig.wallet.ui.components.buttons.VsButtonSize
import com.vultisig.wallet.ui.components.buttons.VsButtonVariant
import com.vultisig.wallet.ui.theme.Theme

@Composable
internal fun NoChainFound(
    modifier: Modifier = Modifier,
    onChooseChains: () -> Unit
) {
    Column(
        modifier = modifier
            .padding(
                horizontal = 40.dp,
            ),
        horizontalAlignment = Alignment.Companion.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "No chains found",
            style = Theme.brockmann.headings.title3,
            color = Theme.colors.text.primary,
            textAlign = TextAlign.Center,
        )

        UiSpacer(
            size = 8.dp
        )

        Text(
            text = "Make sure that the chain you’re looking for is enabled.",
            style = Theme.brockmann.supplementary.footnote,
            color = Theme.colors.text.extraLight,
            textAlign = TextAlign.Center,
        )

        UiSpacer(
            size = 16.dp
        )

        VsButton(
            variant = VsButtonVariant.Primary,
            size = VsButtonSize.Mini,
            iconLeft = R.drawable.write,
            label = "Customize chains",
            onClick = onChooseChains
        )
    }
}

@Preview
@Composable
private fun NoChainFoundPreview() {
    NoChainFound(
        onChooseChains = {}
    )
}
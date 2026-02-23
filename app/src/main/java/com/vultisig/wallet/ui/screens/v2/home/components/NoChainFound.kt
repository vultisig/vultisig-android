package com.vultisig.wallet.ui.screens.v2.home.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.UiIcon
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.buttons.VsButton
import com.vultisig.wallet.ui.components.buttons.VsButtonSize
import com.vultisig.wallet.ui.components.buttons.VsButtonVariant
import com.vultisig.wallet.ui.theme.Theme

@Composable
internal fun NoChainFound(
    modifier: Modifier = Modifier,
    isChainSelectionEnabled: Boolean = true,
    onChooseChains: () -> Unit,
) {
    Column(
        modifier = modifier
            .padding(
                horizontal = 40.dp,
                vertical = 16.dp,
            ),
        horizontalAlignment = Alignment.Companion.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {

        UiIcon(
            drawableResId = R.drawable.crypto,
            size = 26.dp,
            tint = Theme.v2.colors.primary.accent4,
        )
        UiSpacer(
            size = 12.dp
        )

        Text(
            text = stringResource(R.string.no_chains_found),
            style = Theme.brockmann.headings.title3,
            color = Theme.v2.colors.text.primary,
            textAlign = TextAlign.Center,
        )

        UiSpacer(
            size = 8.dp
        )

        Text(
            text = stringResource(R.string.no_chain_make_sure_that),
            style = Theme.brockmann.supplementary.footnote,
            color = Theme.v2.colors.text.tertiary,
            textAlign = TextAlign.Center,
        )

        // Hidden for KeyImport vaults where chains are fixed at import time
        if (isChainSelectionEnabled) {
            UiSpacer(
                size = 16.dp
            )

            VsButton(
                variant = VsButtonVariant.Primary,
                size = VsButtonSize.Mini,
                iconLeft = R.drawable.write,
                label = stringResource(R.string.no_chain_customize_chains),
                onClick = onChooseChains
            )
        }
    }
}

@Preview
@Composable
private fun NoChainFoundPreview() {
    NoChainFound(
        onChooseChains = {}
    )
}
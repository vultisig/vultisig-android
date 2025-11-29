package com.vultisig.wallet.ui.components.errors

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.buttons.VsButton
import com.vultisig.wallet.ui.components.buttons.VsButtonVariant
import com.vultisig.wallet.ui.theme.Theme

@Composable
fun ErrorView(
    title: String,
    description: String,
    onTryAgainClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier,
    ) {

        Text(
            text = title,
            style = Theme.brockmann.headings.title2,
            color = Theme.v2.colors.alerts.error,
            textAlign = TextAlign.Center,
        )

        UiSpacer(12.dp)

        Text(
            text = description,
            style = Theme.brockmann.body.s.medium,
            color = Theme.v2.colors.text.extraLight,
            textAlign = TextAlign.Center,
        )

        UiSpacer(32.dp)

        VsButton(
            label = stringResource(R.string.error_view_try_again_button),
            variant = VsButtonVariant.Secondary,
            onClick = onTryAgainClick,
        )

    }
}

@Preview
@Composable
private fun ErrorViewPreview() {
    ErrorView(
        title = "Something went wrong",
        description = "Please try again later",
        onTryAgainClick = {},
    )
}
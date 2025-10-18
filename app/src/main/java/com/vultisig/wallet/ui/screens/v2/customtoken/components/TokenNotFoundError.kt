package com.vultisig.wallet.ui.screens.v2.customtoken.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.buttons.VsButton
import com.vultisig.wallet.ui.theme.Theme

@Composable
internal fun TokenNotFoundError(
    onRetryClick: () -> Unit,
) {
    Column(
        modifier = Modifier.Companion
            .fillMaxWidth(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.Companion.CenterHorizontally
    ) {
        Image(
            painter = painterResource(id = R.drawable.iconcrypto),
            contentDescription = "token not found",
            modifier = Modifier.Companion.size(20.dp)
        )
        UiSpacer(size = 12.dp)
        Text(
            text = stringResource(R.string.custom_token_token_not_found),
            color = Theme.colors.text.primary,
            style = Theme.brockmann.headings.title3
        )
        UiSpacer(size = 8.dp)
        Text(
            text = stringResource(R.string.custom_token_not_found_desc),
            color = Theme.colors.text.extraLight,
            style = Theme.brockmann.supplementary.footnote,
            textAlign = TextAlign.Companion.Center,
            modifier = Modifier.Companion
                .padding(
                    horizontal = 50.dp
                )
        )
        UiSpacer(
            size = 16.dp
        )

        VsButton(
            label = "Retry",
            modifier = Modifier.Companion.fillMaxWidth(),
            onClick = onRetryClick
        )
    }
}

@Preview
@Composable
private fun TokenNotFoundErrorPreview() {
    TokenNotFoundError(
        onRetryClick = {}
    )
}

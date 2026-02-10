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
import com.vultisig.wallet.ui.components.v2.containers.ContainerType
import com.vultisig.wallet.ui.components.v2.containers.CornerType
import com.vultisig.wallet.ui.components.v2.containers.V2Container
import com.vultisig.wallet.ui.theme.Theme

@Composable
internal fun TokenNotFoundError(
    onRetryClick: () -> Unit,
) {
    V2Container(
        type = ContainerType.SECONDARY,
        cornerType = CornerType.RoundedCornerShape(size = 12.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Image(
                painter = painterResource(id = R.drawable.crypto_outline),
                contentDescription = "token not found",
                modifier = Modifier.size(24.dp)
            )
            UiSpacer(size = 12.dp)
            Text(
                text = stringResource(R.string.custom_token_token_not_found),
                color = Theme.v2.colors.text.primary,
                style = Theme.brockmann.headings.title3
            )
            UiSpacer(size = 8.dp)
            Text(
                text = stringResource(R.string.custom_token_not_found_desc),
                color = Theme.v2.colors.text.tertiary,
                style = Theme.brockmann.supplementary.footnote,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .padding(
                        horizontal = 50.dp
                    )
            )
            UiSpacer(
                size = 16.dp
            )

            VsButton(
                label = stringResource(R.string.retry),
                modifier = Modifier.fillMaxWidth(),
                onClick = onRetryClick
            )
            UiSpacer(
                size = 4.dp
            )
        }
    }
}

@Preview
@Composable
private fun TokenNotFoundErrorPreview() {
    TokenNotFoundError(
        onRetryClick = {}
    )
}

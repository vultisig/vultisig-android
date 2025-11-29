package com.vultisig.wallet.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Alignment.Companion.CenterHorizontally
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.buttons.VsButton
import com.vultisig.wallet.ui.components.buttons.VsButtonVariant
import com.vultisig.wallet.ui.theme.Theme

@Composable
internal fun ErrorView(
    errorLabel: String,
    buttonText: String,
    infoText: String? = null,
    onButtonClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .background(
                color = Theme.v2.colors.backgrounds.primary,
            )
            .padding(
                vertical = 24.dp,
                horizontal = 16.dp,
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            horizontalAlignment = CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            Image(
                painter = painterResource(id = R.drawable.danger),
                contentDescription = stringResource(R.string.danger_icon),
                alignment = Alignment.Center
            )

            UiSpacer(24.dp)

            Text(
                text = errorLabel,
                style = Theme.brockmann.headings.title2,
                color = Theme.v2.colors.alerts.warning,
                textAlign = TextAlign.Center
            )

            UiSpacer(12.dp)

            if (infoText != null) {
                Text(
                    text = infoText,
                    style = Theme.brockmann.body.s.medium,
                    color = Theme.v2.colors.text.extraLight,
                    textAlign = TextAlign.Center
                )

                UiSpacer(10.dp)
            }


            VsButton(
                label = buttonText,
                variant = VsButtonVariant.Secondary,
                onClick = onButtonClick,
                modifier = Modifier
                    .fillMaxWidth(),
            )
        }

        AppVersionText()
    }
}

@Preview(showBackground = true)
@Composable
private fun PreviewKeysignError() {
    ErrorView(
        errorLabel = stringResource(R.string.signing_error_please_try_again_s, ""),
        buttonText = stringResource(R.string.try_again),
        onButtonClick = {},
    )
}
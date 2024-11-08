package com.vultisig.wallet.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import com.vultisig.wallet.ui.theme.Theme

@Composable
internal fun ErrorView(
    errorLabel: String,
    buttonText: String,
    infoText: String? = null,
    onButtonClick: () -> Unit,
) {
    Column(Modifier.background(Theme.colors.oxfordBlue800)) {
        UiSpacer(weight = 1f)

        Column(
            Modifier
                .fillMaxWidth()
                .padding(
                    start = 81.dp,
                    end = 81.dp,
                ),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Image(
                painter = painterResource(id = R.drawable.danger),
                contentDescription = stringResource(R.string.danger_icon),
                alignment = Alignment.Center
            )
            Text(
                modifier = Modifier.padding(top = 9.dp),
                text = errorLabel,
                style = Theme.menlo.subtitle1,
                color = Theme.colors.neutral0,
                textAlign = TextAlign.Center
            )
        }

        UiSpacer(weight = 1f)

        Column (
            horizontalAlignment = CenterHorizontally,
        ){
            if (!infoText.isNullOrEmpty()) {
                InformationNote(
                    modifier = Modifier.padding(horizontal = 8.dp),
                    text = infoText,
                )
            } else {
                AppVersionText()
            }

            MultiColorButton(
                text = buttonText,
                minHeight = 44.dp,
                backgroundColor = Theme.colors.turquoise800,
                textColor = Theme.colors.oxfordBlue800,
                iconColor = Theme.colors.turquoise800,
                textStyle = Theme.montserrat.subtitle1,
                onClick = onButtonClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(all = 16.dp),
            )
        }
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
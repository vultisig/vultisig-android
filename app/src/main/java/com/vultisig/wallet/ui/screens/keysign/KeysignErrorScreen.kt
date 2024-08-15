package com.vultisig.wallet.ui.screens.keysign

import ErrorView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import com.vultisig.wallet.ui.components.InformationNote
import com.vultisig.wallet.ui.components.MultiColorButton
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.theme.Theme


@Composable
internal fun KeysignErrorScreen(
    errorMessage: String = "",
    tryAgain: () -> Unit,
) {
    ErrorView(
        errorLabel = stringResource(R.string.signing_error_please_try_again_s, errorMessage),
        buttonText = stringResource(R.string.try_again),
        infoText = stringResource(R.string.bottom_warning_msg_keygen_error_screen),
        onButtonClick = tryAgain,
    )
}


@Preview(showBackground = true, name = "KeysignErrorScreen Preview")
@Composable
private fun PreviewKeysignError() {
    ErrorView(
        errorLabel = stringResource(R.string.signing_error_please_try_again_s, ""),
        buttonText = stringResource(R.string.try_again),
        infoText = stringResource(R.string.bottom_warning_msg_keygen_error_screen),
        onButtonClick = {},
    )
}
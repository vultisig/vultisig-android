package com.vultisig.wallet.ui.screens.keysign

import ErrorView
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.vultisig.wallet.R


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
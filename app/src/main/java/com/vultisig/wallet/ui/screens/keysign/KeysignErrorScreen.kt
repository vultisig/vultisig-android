package com.vultisig.wallet.ui.screens.keysign

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.ErrorView


@Composable
internal fun KeysignErrorScreen(
    errorMessage: String = "",
    isSwap: Boolean = false,
    tryAgain: () -> Unit,
) {
    val errorLabel = when {
        errorMessage.contains("insufficient funds") && isSwap ->
            stringResource(R.string.signing_error_insufficient_funds_swap)
        else ->
            stringResource(R.string.signing_error_please_try_again_s, errorMessage)
    }
    val infoText = when {
        errorMessage.contains("insufficient funds", true) && isSwap ->
            null
        else ->
            stringResource(R.string.bottom_warning_msg_keygen_error_screen)
    }

    ErrorView(
        errorLabel = errorLabel,
        buttonText = stringResource(R.string.try_again),
        infoText = infoText,
        onButtonClick = tryAgain,
    )
}



@Preview(showBackground = true, name = "KeysignErrorScreen Preview")
@Composable
private fun PreviewKeysignError() {
    ErrorView(
        errorLabel = stringResource(R.string.signing_error_please_try_again_s, ""),
        buttonText = stringResource(R.string.try_again),
        infoText = null,
        onButtonClick = {},
    )
}
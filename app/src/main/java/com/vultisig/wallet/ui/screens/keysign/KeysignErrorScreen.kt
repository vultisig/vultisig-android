package com.vultisig.wallet.ui.screens.keysign

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.errors.ErrorView


@Composable
internal fun KeysignErrorScreen(
    errorMessage: String = "",
    tryAgain: () -> Unit,
) {
    val errorLabel: String
    val infoText: String?
    when {
        errorMessage.contains("Blockhash not found") -> {
            errorLabel = stringResource(R.string.signing_error_transaction_timeout)
            infoText = null
        }
        errorMessage.contains("insufficient funds") -> {
            errorLabel = stringResource(R.string.signing_error_insufficient_funds)
            infoText = null
        }
        errorMessage.contains("failed to calculate bob mid and bob_mic_mc") -> {
            errorLabel = stringResource(R.string.signing_error_mixed_reshare)
            infoText = null
        }
        else -> {
            errorLabel = stringResource(R.string.signing_error_please_try_again_s, errorMessage)
            infoText = stringResource(R.string.bottom_warning_msg_keygen_error_screen)
        }
    }

    ErrorView(
        title = errorLabel,
        buttonText = stringResource(R.string.try_again),
        description = infoText,
        onButtonClick = tryAgain,
    )
}



@Preview(showBackground = true, name = "KeysignErrorScreen Preview")
@Composable
private fun PreviewKeysignError() {
    ErrorView(
        title = stringResource(R.string.signing_error_please_try_again_s, "some errors"),
        buttonText = stringResource(R.string.try_again),
        description = stringResource(R.string.bottom_warning_msg_keygen_error_screen),
        onButtonClick = {},
    )
}
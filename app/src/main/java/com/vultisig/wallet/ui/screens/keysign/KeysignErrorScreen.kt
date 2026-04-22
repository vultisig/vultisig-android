package com.vultisig.wallet.ui.screens.keysign

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.errors.ErrorView
import com.vultisig.wallet.ui.components.errors.ErrorViewButtonUiModel
import com.vultisig.wallet.ui.utils.UiText
import com.vultisig.wallet.ui.utils.asString

@Composable
internal fun KeysignErrorScreen(
    errorMessage: UiText = UiText.Empty,
    tryAgain: () -> Unit,
    onBack: (() -> Unit)? = null,
) {
    val errorMessageString = errorMessage.asString()
    val errorLabel: String
    val infoText: String?
    when {
        errorMessageString.contains("Blockhash not found") -> {
            errorLabel = stringResource(R.string.signing_error_transaction_timeout)
            infoText = null
        }
        errorMessageString.contains("insufficient funds") -> {
            errorLabel = stringResource(R.string.signing_error_insufficient_funds)
            infoText = null
        }
        errorMessageString.contains("failed to calculate bob mid and bob_mic_mc") -> {
            errorLabel = stringResource(R.string.signing_error_mixed_reshare)
            infoText = null
        }
        else -> {
            errorLabel =
                stringResource(R.string.signing_error_please_try_again_s, errorMessageString)
            infoText = stringResource(R.string.bottom_warning_msg_keygen_error_screen)
        }
    }

    ErrorView(
        title = errorLabel,
        description = infoText,
        buttonUiModel =
            ErrorViewButtonUiModel(text = stringResource(R.string.try_again), onClick = tryAgain),
        onBack = onBack,
    )
}

@Preview(showBackground = true, name = "KeysignErrorScreen Preview")
@Composable
private fun PreviewKeysignError() {
    ErrorView(
        title = stringResource(R.string.signing_error_please_try_again_s, "some errors"),
        description = stringResource(R.string.bottom_warning_msg_keygen_error_screen),
        buttonUiModel =
            ErrorViewButtonUiModel(text = stringResource(R.string.try_again), onClick = {}),
    )
}

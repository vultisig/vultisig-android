package com.vultisig.wallet.ui.screens.keysign

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.vultisig.wallet.R
import com.vultisig.wallet.data.api.errors.CosmosBroadcastException
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
        errorMessageString.contains(CosmosBroadcastException.BROADCAST_FAILURE_MARKER) -> {
            // SEQUENCE_MISMATCH_MARKER is itself prefixed with BROADCAST_FAILURE_MARKER, so the
            // sequence-mismatch check has to live inside the broadcast-failure branch — otherwise
            // a future reorder of these two branches would silently route every sequence mismatch
            // into the generic "rejected" copy.
            if (errorMessageString.contains(CosmosBroadcastException.SEQUENCE_MISMATCH_MARKER)) {
                errorLabel = stringResource(R.string.signing_error_sequence_mismatch)
                infoText = null
            } else {
                val detail =
                    errorMessageString
                        .substringAfter(CosmosBroadcastException.BROADCAST_FAILURE_MARKER)
                        .trimStart(':', ' ')
                        .let { raw ->
                            if (raw.length > BROADCAST_DETAIL_MAX_LENGTH) {
                                raw.take(BROADCAST_DETAIL_MAX_LENGTH).trimEnd() + "…"
                            } else raw
                        }
                errorLabel =
                    if (detail.isBlank()) {
                        stringResource(R.string.signing_error_broadcast_rejected)
                    } else {
                        stringResource(R.string.signing_error_broadcast_rejected_s, detail)
                    }
                infoText = null
            }
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

/**
 * Cap on the node-supplied `raw_log` snippet shown in `signing_error_broadcast_rejected_s`. The raw
 * text is unbounded, untranslated and aimed at validators, so anything past this length is
 * truncated with an ellipsis before being interpolated into the user-facing label.
 */
private const val BROADCAST_DETAIL_MAX_LENGTH = 120

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

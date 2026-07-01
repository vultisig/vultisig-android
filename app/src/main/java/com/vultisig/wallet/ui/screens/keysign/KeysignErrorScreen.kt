package com.vultisig.wallet.ui.screens.keysign

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.vultisig.wallet.R
import com.vultisig.wallet.data.api.errors.CosmosBroadcastException
import com.vultisig.wallet.data.chains.helpers.SOLANA_MISSING_TOKEN_ACCOUNT_PREFIX
import com.vultisig.wallet.ui.components.errors.ErrorState
import com.vultisig.wallet.ui.components.errors.ErrorView
import com.vultisig.wallet.ui.components.errors.ErrorViewButtonUiModel
import com.vultisig.wallet.ui.utils.UiText
import com.vultisig.wallet.ui.utils.asString

internal data class SigningError(
    val title: String,
    val description: String?,
    val errorState: ErrorState,
)

/**
 * Maps a raw keysign error message to friendly title/subtitle + icon variant. Leads with a
 * human-readable message; the raw text is kept one tap away via the "Show exact error" disclosure
 * on [ErrorView]. Recoverable / precondition errors use the amber warning variant; hard failures
 * use the red critical variant.
 */
@Composable
internal fun resolveSigningError(rawMessage: String): SigningError {
    // Normalize away spaces/underscores/case so a single check matches both the RPC's human wording
    // ("Blockhash not found", "insufficient lamports") and Solana's camelCase enum forms
    // ("BlockhashNotFound", "BlockHeightExceeded") that error.data.err carries.
    val normalized = rawMessage.lowercase().replace(" ", "").replace("_", "")
    return when {
        // Blockhash expiry — both RPC wordings map to the same "sign again" copy.
        normalized.contains("blockhashnotfound") || normalized.contains("blockheightexceeded") ->
            SigningError(
                title = stringResource(R.string.signing_error_transaction_failed_title),
                description = stringResource(R.string.signing_error_transaction_timeout),
                errorState = ErrorState.CRITICAL,
            )
        normalized.contains("insufficientfunds") || normalized.contains("insufficientlamports") ->
            SigningError(
                title = stringResource(R.string.error_insufficient_funds_title),
                description = stringResource(R.string.signing_error_insufficient_funds),
                errorState = ErrorState.WARNING,
            )
        rawMessage.contains(SOLANA_MISSING_TOKEN_ACCOUNT_PREFIX) -> {
            val ticker = rawMessage.substringAfter(SOLANA_MISSING_TOKEN_ACCOUNT_PREFIX).trim()
            SigningError(
                title = stringResource(R.string.signing_error_transaction_failed_title),
                description =
                    stringResource(R.string.signing_error_token_account_not_found_s, ticker),
                errorState = ErrorState.CRITICAL,
            )
        }
        rawMessage.contains("failed to calculate bob mid and bob_mic_mc") ->
            SigningError(
                title = stringResource(R.string.signing_error_transaction_failed_title),
                description = stringResource(R.string.signing_error_mixed_reshare),
                errorState = ErrorState.WARNING,
            )
        rawMessage.contains(CosmosBroadcastException.BROADCAST_FAILURE_MARKER) -> {
            // SEQUENCE_MISMATCH_MARKER is itself prefixed with BROADCAST_FAILURE_MARKER, so the
            // sequence-mismatch check has to live inside the broadcast-failure branch — otherwise
            // a future reorder of these two branches would silently route every sequence mismatch
            // into the generic "rejected" copy.
            if (rawMessage.contains(CosmosBroadcastException.SEQUENCE_MISMATCH_MARKER)) {
                SigningError(
                    title = stringResource(R.string.signing_error_transaction_failed_title),
                    description = stringResource(R.string.signing_error_sequence_mismatch),
                    errorState = ErrorState.WARNING,
                )
            } else {
                val detail =
                    rawMessage
                        .substringAfter(CosmosBroadcastException.BROADCAST_FAILURE_MARKER)
                        .trimStart(':', ' ')
                        .let { raw ->
                            if (raw.length > BROADCAST_DETAIL_MAX_LENGTH) {
                                raw.take(BROADCAST_DETAIL_MAX_LENGTH).trimEnd() + "…"
                            } else raw
                        }
                SigningError(
                    title = stringResource(R.string.signing_error_transaction_failed_title),
                    description =
                        if (detail.isBlank()) {
                            stringResource(R.string.signing_error_broadcast_rejected)
                        } else {
                            stringResource(R.string.signing_error_broadcast_rejected_s, detail)
                        },
                    errorState = ErrorState.CRITICAL,
                )
            }
        }
        // On-chain rejection at broadcast: the app runs no client-side simulation, so a
        // "simulation failed" can only come from the node's preflight. This is a network rejection,
        // NOT a device/connection timeout — surface it as such, with the on-chain reason.
        normalized.contains("simulationfailed") ->
            SigningError(
                title = stringResource(R.string.signing_error_transaction_failed_title),
                description = onChainRejectionDescription(rawMessage),
                errorState = ErrorState.CRITICAL,
            )
        else ->
            SigningError(
                title = stringResource(R.string.signing_error_transaction_failed_title),
                description = stringResource(R.string.signing_error_transaction_failed_description),
                errorState = ErrorState.CRITICAL,
            )
    }
}

/**
 * Builds the description for an on-chain broadcast rejection, appending the node-supplied reason
 * (the text after "simulation failed", e.g. `AccountLoadedTwice`) when present. Reuses the same
 * "rejected by the network" copy as the Cosmos broadcast path.
 */
@Composable
private fun onChainRejectionDescription(rawMessage: String): String {
    // Match the marker the same way the branch does (case + spaces/underscores insensitive) so the
    // on-chain reason after it isn't lost for wordings like "SimulationFailed: AccountLoadedTwice".
    val detail =
        SIMULATION_FAILED_MARKER.find(rawMessage)
            ?.let { rawMessage.substring(it.range.last + 1) }
            .orEmpty()
            .trimStart(':', ' ')
            .let { raw ->
                if (raw.length > BROADCAST_DETAIL_MAX_LENGTH) {
                    raw.take(BROADCAST_DETAIL_MAX_LENGTH).trimEnd() + "…"
                } else raw
            }
    return if (detail.isBlank()) {
        stringResource(R.string.signing_error_broadcast_rejected)
    } else {
        stringResource(R.string.signing_error_broadcast_rejected_s, detail)
    }
}

@Composable
internal fun KeysignErrorScreen(
    errorMessage: UiText = UiText.Empty,
    tryAgain: () -> Unit,
    onBack: (() -> Unit)? = null,
) {
    val rawMessage = errorMessage.asString()
    val signingError = resolveSigningError(rawMessage)

    ErrorView(
        title = signingError.title,
        description = signingError.description,
        errorState = signingError.errorState,
        rawError = rawMessage.ifBlank { null },
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

/** Matches the RPC "simulation failed" marker regardless of case and space/underscore wording. */
private val SIMULATION_FAILED_MARKER = Regex("(?i)simulation[ _]?failed")

@Preview(showBackground = true, name = "KeysignErrorScreen Preview")
@Composable
private fun PreviewKeysignError() {
    ErrorView(
        title = stringResource(R.string.signing_error_transaction_failed_title),
        description = stringResource(R.string.signing_error_transaction_failed_description),
        errorState = ErrorState.CRITICAL,
        rawError = "javax.crypto.AEADBadTagException: BAD_DECRYPT",
        buttonUiModel =
            ErrorViewButtonUiModel(text = stringResource(R.string.try_again), onClick = {}),
    )
}

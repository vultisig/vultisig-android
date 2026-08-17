package com.vultisig.wallet.ui.screens.passcode

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.errors.ErrorState
import com.vultisig.wallet.ui.components.errors.ErrorView
import com.vultisig.wallet.ui.components.errors.ErrorViewButtonUiModel
import com.vultisig.wallet.ui.models.FILE_ALLOWED_MIME_TYPES
import com.vultisig.wallet.ui.models.keysign.KeysignPasswordUiModel
import com.vultisig.wallet.ui.models.passcode.KeyshareRecoveryUiModel
import com.vultisig.wallet.ui.models.passcode.KeyshareRecoveryViewModel
import com.vultisig.wallet.ui.screens.keysign.KeysignPasswordBottomSheet
import com.vultisig.wallet.ui.utils.ActivityResultContractsGetContentWithMimeTypes
import com.vultisig.wallet.ui.utils.UiText

/**
 * Shown when encrypted keyshares outlive the credentials that unwrap them: no passcode can open
 * them again, and the backup the user already holds is the way back.
 *
 * The picker is launched from here rather than routed to: the import screen renders in the
 * activity's own window, which is under this one and behind the gate's cover. Launching it from a
 * dialog window works all the same — the result registry resolves through the wrapped context to
 * the host activity — and backgrounding for the picker cannot move this state, since locking only
 * ever demotes an unlocked app.
 */
@Composable
internal fun KeyshareRecoveryScreen(model: KeyshareRecoveryViewModel = hiltViewModel()) {
    val state by model.state.collectAsStateWithLifecycle()

    val picker =
        rememberLauncherForActivityResult(
            ActivityResultContractsGetContentWithMimeTypes(FILE_ALLOWED_MIME_TYPES)
        ) { uri: Uri? ->
            model.onBackupPicked(uri)
        }

    KeyshareRecoveryScreen(
        state = state,
        passwordFieldState = model.passwordTextFieldState,
        onRestoreClick = { picker.launch("*/*") },
        onRetryClick = model::onRetry,
        onPasswordVisibilityToggle = model::onPasswordVisibilityToggled,
        onPasswordEntered = model::onPasswordEntered,
        onPasswordPromptDismissed = model::onPasswordPromptDismissed,
    )
}

@Composable
private fun KeyshareRecoveryScreen(
    state: KeyshareRecoveryUiModel,
    passwordFieldState: TextFieldState,
    onRestoreClick: () -> Unit,
    onRetryClick: () -> Unit,
    onPasswordVisibilityToggle: () -> Unit,
    onPasswordEntered: () -> Unit,
    onPasswordPromptDismissed: () -> Unit,
) {
    if (state.isPasswordPromptVisible) {
        KeysignPasswordBottomSheet(
            subtitle = stringResource(R.string.import_file_screen_enter_password_sub),
            confirmButtonLabel = stringResource(R.string.fast_vault_password_screen_next),
            state =
                KeysignPasswordUiModel(
                    isPasswordVisible = !state.isPasswordObfuscated,
                    passwordError = state.passwordError?.let { UiText.StringResource(it) },
                ),
            passwordFieldState = passwordFieldState,
            onPasswordVisibilityToggle = onPasswordVisibilityToggle,
            onContinueClick = onPasswordEntered,
            onBackClick = onPasswordPromptDismissed,
        )
    }

    ErrorView(
        title = stringResource(R.string.passcode_key_unavailable_title),
        description = stringResource(state.message),
        errorState = ErrorState.WARNING,
        buttonUiModel =
            ErrorViewButtonUiModel(
                text = stringResource(R.string.passcode_key_unavailable_restore),
                onClick = onRestoreClick,
                // Deriving the restored vault's addresses takes seconds, and nothing else on this
                // screen moves while it runs.
                isLoading = state.isRestoring,
            ),
        // Above the restore button, because a keystore that came back is the outcome that costs
        // the user nothing.
        secondaryButtonUiModel =
            ErrorViewButtonUiModel(
                text = stringResource(R.string.try_again),
                onClick = onRetryClick,
            ),
    )
}

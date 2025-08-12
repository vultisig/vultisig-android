package com.vultisig.wallet.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import com.vultisig.wallet.R
import com.vultisig.wallet.data.usecases.MIME_TYPE_VAULT
import com.vultisig.wallet.ui.components.UiAlertDialog
import com.vultisig.wallet.ui.components.inputs.VsTextInputFieldInnerState
import com.vultisig.wallet.ui.models.BackupPasswordViewModel
import com.vultisig.wallet.ui.models.keygen.FastVaultPasswordUiModel
import com.vultisig.wallet.ui.screens.keygen.FastVaultPasswordScreen
import com.vultisig.wallet.ui.utils.RequestWriteFilePermissionOnceIfNotGranted
import com.vultisig.wallet.ui.utils.asString
import com.vultisig.wallet.ui.utils.file.RequestCreateDocument


@Composable
internal fun BackupPasswordScreen(
    model: BackupPasswordViewModel = hiltViewModel<BackupPasswordViewModel>()
) {
    val state by model.state.collectAsState()

    RequestWriteFilePermissionOnceIfNotGranted(
        onRequestPermissionResult = model::onPermissionResult,
    )

    RequestCreateDocument(
        mimeType = MIME_TYPE_VAULT,
        onDocumentCreated = model::saveContentToUriResult,
        createDocumentRequestFlow = model.createDocumentRequestFlow,
    )

    val error = state.error
    if (error != null) {
        UiAlertDialog(
            title = stringResource(R.string.dialog_default_error_title),
            text = error.asString(),
            confirmTitle = stringResource(R.string.try_again),
            onDismiss = model::dismissError,
        )
    }

    FastVaultPasswordScreen(
        title = stringResource(R.string.backup_password_topbar_title),
        state = FastVaultPasswordUiModel(
            isMoreInfoVisible = state.isMoreInfoVisible,
            isPasswordVisible = state.isPasswordVisible,
            isConfirmPasswordVisible = state.isConfirmPasswordVisible,
            isNextButtonEnabled = state.isNextButtonEnabled,
            errorMessage = state.passwordErrorMessage,
            innerState = if (state.passwordErrorMessage != null)
                VsTextInputFieldInnerState.Error
            else VsTextInputFieldInnerState.Default
        ),
        passwordTextFieldState = model.passwordTextFieldState,
        confirmPasswordTextFieldState = model.confirmPasswordTextFieldState,
        onBackClick = model::back,
        onNextClick = model::backupEncryptedVault,
        onShowMoreInfo =model::showMoreInfo,
        onHideMoreInfo = model::hideMoreInfo,
        onTogglePasswordVisibilityClick = model::togglePasswordVisibility,
        onToggleConfirmPasswordVisibilityClick = model::toggleConfirmPasswordVisibility
    )
}
package com.vultisig.wallet.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.layout.boundsInParent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.UiAlertDialog
import com.vultisig.wallet.ui.components.UiIcon
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.banners.Banner
import com.vultisig.wallet.ui.components.banners.BannerVariant
import com.vultisig.wallet.ui.components.buttons.VsButton
import com.vultisig.wallet.ui.components.buttons.VsButtonState
import com.vultisig.wallet.ui.components.inputs.VsTextInputField
import com.vultisig.wallet.ui.components.inputs.VsTextInputFieldInnerState
import com.vultisig.wallet.ui.components.inputs.VsTextInputFieldType
import com.vultisig.wallet.ui.components.v2.scaffold.V2Scaffold
import com.vultisig.wallet.ui.components.v2.utils.roundToPx
import com.vultisig.wallet.ui.models.auth.BackupPasswordState
import com.vultisig.wallet.ui.models.auth.BackupPasswordViewModel
import com.vultisig.wallet.ui.screens.swap.components.HintBox
import com.vultisig.wallet.ui.screens.v3.onboarding.components.OnboardingResponsiveBottomBar
import com.vultisig.wallet.ui.screens.v3.onboarding.components.OnboardingResponsiveContainer
import com.vultisig.wallet.ui.theme.Theme
import com.vultisig.wallet.ui.utils.RequestWriteFilePermissionOnceIfNotGranted
import com.vultisig.wallet.ui.utils.UiText
import com.vultisig.wallet.ui.utils.asString
import com.vultisig.wallet.ui.utils.file.RequestCreateDocument

@Composable
internal fun BackupPasswordScreen(
    model: BackupPasswordViewModel = hiltViewModel<BackupPasswordViewModel>()
) {
    val state by model.state.collectAsState()

    RequestWriteFilePermissionOnceIfNotGranted(
        onRequestPermissionResult = model::onPermissionResult
    )

    RequestCreateDocument(
        mimeType = state.mimeType.value,
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

    BackupPasswordScreen(
        state = state,
        passwordTextFieldState = model.passwordTextFieldState,
        confirmPasswordTextFieldState = model.confirmPasswordTextFieldState,
        onBackClick = model::back,
        onBackupClick = model::backupEncryptedVault,
        onShowMoreInfo = model::showMoreInfo,
        onHideMoreInfo = model::hideMoreInfo,
        onTogglePasswordVisibilityClick = model::togglePasswordVisibility,
        onToggleConfirmPasswordVisibilityClick = model::toggleConfirmPasswordVisibility,
    )
}

@Composable
internal fun BackupPasswordScreen(
    state: BackupPasswordState,
    passwordTextFieldState: TextFieldState,
    confirmPasswordTextFieldState: TextFieldState,
    onBackClick: () -> Unit,
    onBackupClick: () -> Unit,
    onShowMoreInfo: () -> Unit,
    onHideMoreInfo: () -> Unit,
    onTogglePasswordVisibilityClick: () -> Unit,
    onToggleConfirmPasswordVisibilityClick: () -> Unit,
) {
    var hintBoxOffset by remember { mutableIntStateOf(0) }
    val defaultVerticalPadding = 12.dp.roundToPx()
    val topbarHeight = 64.dp.roundToPx()
    V2Scaffold(
        onBackClick = onBackClick,
        content = {
            OnboardingResponsiveContainer {
                Column {
                    Text(
                        text = stringResource(R.string.backup_password_device_title),
                        style = Theme.brockmann.headings.largeTitle,
                        color = Theme.v2.colors.text.primary,
                    )

                    UiSpacer(16.dp)

                    Banner(
                        text = stringResource(R.string.fast_vault_password_screen_warning),
                        variant = BannerVariant.Warning,
                        showLeadingIcon = false,
                        actions = {
                            UiIcon(
                                drawableResId = R.drawable.ic_info,
                                size = 16.dp,
                                tint = Theme.v2.colors.alerts.warning,
                                contentDescription =
                                    stringResource(R.string.fast_vault_password_screen_hint_title),
                                onClick = onShowMoreInfo,
                            )
                        },
                        modifier =
                            Modifier.fillMaxWidth().onGloballyPositioned { position ->
                                hintBoxOffset =
                                    position.boundsInParent().bottom.toInt() + topbarHeight -
                                        defaultVerticalPadding
                            },
                    )

                    Column(
                        modifier = Modifier.fillMaxWidth().heightIn(min = 178.dp),
                        verticalArrangement =
                            Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
                    ) {
                        val focusRequester = remember { FocusRequester() }

                        LaunchedEffect(Unit) { focusRequester.requestFocus() }

                        VsTextInputField(
                            textFieldState = passwordTextFieldState,
                            hint =
                                stringResource(R.string.fast_vault_password_screen_password_hint),
                            type =
                                VsTextInputFieldType.Password(
                                    isVisible = state.isPasswordVisible,
                                    onVisibilityClick = onTogglePasswordVisibilityClick,
                                ),
                            focusRequester = focusRequester,
                            imeAction = ImeAction.Next,
                            modifier = Modifier.testTag("BackupPasswordScreen.passwordField"),
                        )

                        VsTextInputField(
                            textFieldState = confirmPasswordTextFieldState,
                            hint =
                                stringResource(
                                    R.string.fast_vault_password_screen_reenter_password_hint
                                ),
                            type =
                                VsTextInputFieldType.Password(
                                    isVisible = state.isConfirmPasswordVisible,
                                    onVisibilityClick = onToggleConfirmPasswordVisibilityClick,
                                ),
                            innerState =
                                if (state.passwordErrorMessage != null)
                                    VsTextInputFieldInnerState.Error
                                else VsTextInputFieldInnerState.Default,
                            footNote = (state.passwordErrorMessage ?: UiText.Empty).asString(),
                            imeAction =
                                if (state.isNextButtonEnabled) ImeAction.Go else ImeAction.None,
                            onKeyboardAction = { if (state.isNextButtonEnabled) onBackupClick() },
                            modifier =
                                Modifier.testTag("BackupPasswordScreen.confirmPasswordField"),
                        )
                    }
                }

                HintBox(
                    title = stringResource(R.string.fast_vault_password_screen_hint_title),
                    message = stringResource(R.string.fast_vault_password_screen_hint),
                    offset = IntOffset(x = 0, y = hintBoxOffset),
                    pointerAlignment = Alignment.End,
                    onDismissClick = onHideMoreInfo,
                    modifier = Modifier.padding(horizontal = 16.dp),
                    isVisible = state.isMoreInfoVisible,
                )
            }
        },
        bottomBar = {
            OnboardingResponsiveBottomBar {
                VsButton(
                    label = stringResource(R.string.backup_password_screen_back_up_now),
                    state =
                        if (state.isNextButtonEnabled) VsButtonState.Enabled
                        else VsButtonState.Disabled,
                    modifier =
                        Modifier.fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 24.dp)
                            .testTag("BackupPasswordScreen.backUp"),
                    onClick = onBackupClick,
                )
            }
        },
    )
}

@Composable
@Preview
private fun BackupPasswordScreenPreview() {
    BackupPasswordScreen(
        state = BackupPasswordState(),
        passwordTextFieldState = rememberTextFieldState(),
        confirmPasswordTextFieldState = rememberTextFieldState(),
        onBackClick = {},
        onBackupClick = {},
        onShowMoreInfo = {},
        onHideMoreInfo = {},
        onTogglePasswordVisibilityClick = {},
        onToggleConfirmPasswordVisibilityClick = {},
    )
}

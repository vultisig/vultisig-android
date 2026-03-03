package com.vultisig.wallet.ui.screens.backup

import android.content.Intent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.UiIcon
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.bottomsheet.VsModalBottomSheet
import com.vultisig.wallet.ui.components.buttons.VsButton
import com.vultisig.wallet.ui.components.buttons.VsButtonState
import com.vultisig.wallet.ui.components.buttons.VsButtonVariant
import com.vultisig.wallet.ui.components.clickOnce
import com.vultisig.wallet.ui.components.inputs.VsTextInputField
import com.vultisig.wallet.ui.components.inputs.VsTextInputFieldInnerState
import com.vultisig.wallet.ui.components.inputs.VsTextInputFieldType
import com.vultisig.wallet.ui.components.v2.scaffold.V2Scaffold
import com.vultisig.wallet.ui.theme.Theme
import com.vultisig.wallet.ui.utils.asString

/**
 * Server Backup screen that allows users to request their vault backup share
 * to be resent via email. Collects email and the vault encryption password,
 * then calls the VultiSigner `/vault/resend` endpoint.
 */
@Composable
internal fun ServerBackupScreen(
    model: ServerBackupViewModel = hiltViewModel(),
) {
    val state by model.state.collectAsState()

    ServerBackupScreen(
        state = state,
        emailFieldState = model.emailFieldState,
        passwordFieldState = model.passwordFieldState,
        onEditEmail = model::onEditEmail,
        onClearEmailClick = model::clearEmailInput,
        onTogglePasswordVisibility = model::togglePasswordVisibility,
        onSubmit = model::onSubmit,
        onSuccessClose = model::onSuccessClose,
        onBackClick = model::back,
    )
}

@Composable
private fun ServerBackupScreen(
    state: ServerBackupUiState,
    emailFieldState: TextFieldState,
    passwordFieldState: TextFieldState,
    onEditEmail: () -> Unit,
    onClearEmailClick: () -> Unit,
    onTogglePasswordVisibility: () -> Unit,
    onSubmit: () -> Unit,
    onSuccessClose: () -> Unit,
    onBackClick: () -> Unit,
) {
    val isEmailValid = state.emailInnerState == VsTextInputFieldInnerState.Success
    val isPasswordNotEmpty = passwordFieldState.text.isNotEmpty()
    val canSubmit = isEmailValid && isPasswordNotEmpty && !state.isLoading

    V2Scaffold(
        title = stringResource(R.string.server_backup_title),
        onBackClick = onBackClick,
        bottomBar = {
            VsButton(
                label = stringResource(R.string.server_backup_get_started),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 24.dp),
                onClick = onSubmit,
                state = if (canSubmit) VsButtonState.Enabled else VsButtonState.Disabled,
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            val errorBannerText = state.errorBanner.asString()
            if (errorBannerText.isNotEmpty()) {
                ErrorBanner(text = errorBannerText)
            }

            if (state.isNameConfirmed && state.vaultName.isNotEmpty()) {
                ConfirmedRow(
                    label = stringResource(R.string.server_backup_name_label),
                    value = state.vaultName,
                )
            }

            if (state.isEmailConfirmed) {
                ConfirmedRow(
                    label = stringResource(R.string.server_backup_email_label),
                    value = emailFieldState.text.toString(),
                    onEditClick = onEditEmail,
                )
            } else {
                EmailSection(
                    emailFieldState = emailFieldState,
                    innerState = state.emailInnerState,
                    errorMessage = state.emailError.asString(),
                    onClearClick = onClearEmailClick,
                )
            }

            PasswordSection(
                passwordFieldState = passwordFieldState,
                isPasswordVisible = state.isPasswordVisible,
                onToggleVisibility = onTogglePasswordVisibility,
                onSubmit = onSubmit,
            )

            if (state.isLoading) {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(32.dp),
                        color = Theme.v2.colors.buttons.primary,
                        strokeWidth = 3.dp,
                    )
                }
            }
        }
    }

    if (state.isSuccess) {
        ServerBackupSuccessBottomSheet(
            onClose = onSuccessClose,
        )
    }
}

private val SectionShape = RoundedCornerShape(12.dp)

@Composable
private fun Modifier.sectionCard(): Modifier = this
    .fillMaxWidth()
    .border(1.dp, Theme.v2.colors.border.light, SectionShape)
    .clip(SectionShape)
    .background(Theme.v2.colors.backgrounds.secondary)
    .padding(16.dp)

@Composable
private fun ConfirmedRow(
    label: String,
    value: String,
    onEditClick: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier.sectionCard(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = Theme.brockmann.supplementary.footnote,
                color = Theme.v2.colors.text.tertiary,
            )
            UiSpacer(4.dp)
            Text(
                text = value,
                style = Theme.brockmann.body.s.medium,
                color = Theme.v2.colors.text.primary,
            )
        }

        UiIcon(
            drawableResId = R.drawable.ic_check,
            size = 20.dp,
            tint = Theme.v2.colors.alerts.success,
        )

        if (onEditClick != null) {
            UiSpacer(12.dp)
            UiIcon(
                drawableResId = R.drawable.ic_edit_pencil,
                size = 20.dp,
                tint = Theme.v2.colors.text.tertiary,
                onClick = onEditClick,
            )
        }
    }
}

@Composable
private fun EmailSection(
    emailFieldState: TextFieldState,
    innerState: VsTextInputFieldInnerState,
    errorMessage: String,
    onClearClick: () -> Unit,
) {
    var isFocused by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.sectionCard(),
    ) {
        Text(
            text = stringResource(R.string.server_backup_email_label),
            style = Theme.brockmann.body.s.medium,
            color = Theme.v2.colors.text.primary,
        )

        UiSpacer(12.dp)

        Text(
            text = stringResource(R.string.server_backup_email_description),
            style = Theme.brockmann.supplementary.footnote,
            color = Theme.v2.colors.text.tertiary,
        )

        UiSpacer(12.dp)

        val showClearIcon = isFocused && emailFieldState.text.isNotEmpty()

        VsTextInputField(
            textFieldState = emailFieldState,
            innerState = innerState,
            hint = stringResource(R.string.enter_email_screen_hint),
            footNote = errorMessage.takeIf { it.isNotEmpty() },
            trailingIcon = if (showClearIcon) R.drawable.close_circle else null,
            onTrailingIconClick = onClearClick,
            onFocusChanged = { isFocused = it },
        )
    }
}

@Composable
private fun PasswordSection(
    passwordFieldState: TextFieldState,
    isPasswordVisible: Boolean,
    onToggleVisibility: () -> Unit,
    onSubmit: () -> Unit,
) {
    Column(
        modifier = Modifier.sectionCard(),
    ) {
        Text(
            text = stringResource(R.string.server_backup_password_label),
            style = Theme.brockmann.body.s.medium,
            color = Theme.v2.colors.text.primary,
        )

        UiSpacer(12.dp)

        Text(
            text = stringResource(R.string.server_backup_password_description),
            style = Theme.brockmann.supplementary.footnote,
            color = Theme.v2.colors.text.tertiary,
        )

        UiSpacer(12.dp)

        VsTextInputField(
            textFieldState = passwordFieldState,
            type = VsTextInputFieldType.Password(
                isVisible = isPasswordVisible,
                onVisibilityClick = onToggleVisibility,
            ),
            hint = stringResource(R.string.server_backup_password_label),
            onKeyboardAction = { onSubmit() },
        )
    }
}

@Composable
private fun ErrorBanner(
    text: String,
) {
    val errorColor = Theme.v2.colors.alerts.error
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(SectionShape)
            .background(errorColor.copy(alpha = 0.15f))
            .border(
                width = 1.dp,
                shape = SectionShape,
                color = errorColor.copy(alpha = 0.25f),
            )
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            painter = painterResource(R.drawable.alert),
            contentDescription = null,
            tint = errorColor,
            modifier = Modifier.size(20.dp),
        )
        Text(
            text = text,
            style = Theme.brockmann.supplementary.footnote,
            color = errorColor,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun ServerBackupSuccessBottomSheet(
    onClose: () -> Unit,
) {
    val context = LocalContext.current

    VsModalBottomSheet(
        onDismissRequest = onClose,
        showDragHandle = false,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            UiSpacer(24.dp)

            val infiniteTransition = rememberInfiniteTransition(
                label = "dashes_rotation",
            )
            val dashesRotation by infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = 360f,
                animationSpec = infiniteRepeatable(
                    animation = tween(
                        durationMillis = 40000,
                        easing = LinearEasing,
                    ),
                ),
                label = "dashes_rotation",
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = painterResource(R.drawable.il_server_backup_success_dashes),
                    contentDescription = null,
                    modifier = Modifier
                        .matchParentSize()
                        .rotate(dashesRotation),
                )
                Image(
                    painter = painterResource(R.drawable.il_server_backup_success),
                    contentDescription = null,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            UiSpacer(24.dp)

            Text(
                text = stringResource(R.string.server_backup_success_title),
                style = Theme.brockmann.headings.title1,
                color = Theme.v2.colors.text.primary,
                textAlign = TextAlign.Center,
            )

            UiSpacer(12.dp)

            Text(
                text = stringResource(R.string.server_backup_success_description),
                style = Theme.brockmann.body.s.medium,
                color = Theme.v2.colors.text.tertiary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp),
            )

            UiSpacer(16.dp)

            Text(
                text = stringResource(R.string.server_backup_success_check_email),
                style = Theme.brockmann.body.s.medium.copy(
                    textDecoration = TextDecoration.Underline,
                ),
                color = Theme.v2.colors.buttons.primary,
                modifier = Modifier.clickOnce {
                    val intent = Intent(Intent.ACTION_MAIN).apply {
                        addCategory(Intent.CATEGORY_APP_EMAIL)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    try {
                        context.startActivity(intent)
                    } catch (_: Exception) {
                        // No email client available
                    }
                },
            )

            UiSpacer(32.dp)

            VsButton(
                label = stringResource(R.string.server_backup_success_close),
                variant = VsButtonVariant.Secondary,
                modifier = Modifier.fillMaxWidth(),
                onClick = onClose,
            )

            UiSpacer(24.dp)
        }
    }
}

@Preview
@Composable
private fun ServerBackupScreenPreview() {
    ServerBackupScreen(
        state = ServerBackupUiState(
            vaultName = "Main Vault",
            isNameConfirmed = true,
        ),
        emailFieldState = rememberTextFieldState(),
        passwordFieldState = rememberTextFieldState(),
        onEditEmail = {},
        onClearEmailClick = {},
        onTogglePasswordVisibility = {},
        onSubmit = {},
        onSuccessClose = {},
        onBackClick = {},
    )
}

@Preview
@Composable
private fun ServerBackupSuccessPreview() {
    ServerBackupSuccessBottomSheet(
        onClose = {},
    )
}

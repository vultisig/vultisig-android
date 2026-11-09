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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onPlaced
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
import com.vultisig.wallet.ui.components.bottomsheet.SettingsModalBottomSheet
import com.vultisig.wallet.ui.components.buttons.VsButton
import com.vultisig.wallet.ui.components.buttons.VsButtonState
import com.vultisig.wallet.ui.components.buttons.VsButtonVariant
import com.vultisig.wallet.ui.components.clickOnce
import com.vultisig.wallet.ui.components.inputs.VsTextInputField
import com.vultisig.wallet.ui.components.inputs.VsTextInputFieldInnerState
import com.vultisig.wallet.ui.components.inputs.VsTextInputFieldStyle
import com.vultisig.wallet.ui.components.inputs.VsTextInputFieldType
import com.vultisig.wallet.ui.components.v2.scaffold.V2Scaffold
import com.vultisig.wallet.ui.theme.Theme
import com.vultisig.wallet.ui.theme.v2.V2
import com.vultisig.wallet.ui.utils.asString

/**
 * Server Backup screen that allows users to request their vault backup share to be resent via
 * email. Collects email and the vault encryption password, then calls the VultiSigner
 * `/vault/resend` endpoint.
 */
@Composable
internal fun ServerBackupScreen(model: ServerBackupViewModel = hiltViewModel()) {
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
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp),
                onClick = onSubmit,
                state = if (canSubmit) VsButtonState.Enabled else VsButtonState.Disabled,
            )
        },
    ) {
        val scrollState = rememberScrollState()
        val errorBannerText = state.errorBanner.asString()
        var isPasswordFocused by remember { mutableStateOf(false) }
        var formCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
        var passwordFieldTop by remember { mutableIntStateOf(Int.MAX_VALUE) }

        // The banner is the form's last item, under the password field: a new error or the
        // keyboard opening on that field shows both, or the field and what fits of the banner.
        LaunchedEffect(errorBannerText) {
            if (errorBannerText.isNotEmpty()) {
                withFrameNanos {}
                scrollState.animateScrollTo(minOf(scrollState.maxValue, passwordFieldTop))
            }
        }
        LaunchedEffect(scrollState.maxValue, isPasswordFocused) {
            if (errorBannerText.isNotEmpty() && isPasswordFocused) {
                scrollState.scrollTo(minOf(scrollState.maxValue, passwordFieldTop))
            }
        }

        Column(
            modifier =
                Modifier.fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(horizontal = 8.dp)
                    .onPlaced { formCoordinates = it },
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
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
                onFocusChanged = { isPasswordFocused = it },
                modifier =
                    Modifier.onPlaced { field ->
                        formCoordinates?.let {
                            passwordFieldTop = it.localPositionOf(field, Offset.Zero).y.toInt()
                        }
                    },
            )

            if (state.isLoading) {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(32.dp),
                        color = Theme.v2.colors.buttons.primary,
                        strokeWidth = 3.dp,
                    )
                }
            }

            if (errorBannerText.isNotEmpty()) {
                ErrorBanner(text = errorBannerText)
            }
        }
    }

    if (state.isSuccess) {
        ServerBackupSuccessBottomSheet(onClose = onSuccessClose)
    }
}

private val SectionShape = V2.radius.md
private val BannerShape = V2.radius.xl
private val SENT_TEXT_MAX_WIDTH = 275.dp

/** A confirmed value, sized and drawn like the field it replaces. */
private fun Modifier.confirmedRowCard(): Modifier = composed {
    this.fillMaxWidth()
        .border(1.dp, Theme.v2.colors.variables.bordersExtraLight, SectionShape)
        .clip(SectionShape)
        .background(Theme.v2.colors.variables.inputFieldsBackground)
        .padding(16.dp)
}

@Composable
private fun SectionHeading(title: String, description: String) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = title,
            style = Theme.brockmann.headings.title2,
            color = Theme.v2.colors.text.primary,
            textAlign = TextAlign.Center,
        )
        Text(
            text = description,
            style = Theme.brockmann.body.s.medium,
            color = Theme.v2.colors.text.tertiary,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun ConfirmedRow(label: String, value: String, onEditClick: (() -> Unit)? = null) {
    Row(modifier = Modifier.confirmedRowCard(), verticalAlignment = Alignment.CenterVertically) {
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

    Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
        SectionHeading(
            title = stringResource(R.string.enter_email_screen_title),
            description = stringResource(R.string.server_backup_email_description),
        )

        val showClearIcon = isFocused && emailFieldState.text.isNotEmpty()

        VsTextInputField(
            textFieldState = emailFieldState,
            innerState = innerState,
            hint = stringResource(R.string.enter_email_screen_hint),
            footNote = errorMessage.takeIf { it.isNotEmpty() },
            trailingIcon = if (showClearIcon) R.drawable.close_circle else null,
            onTrailingIconClick = onClearClick,
            onFocusChanged = { isFocused = it },
            style = VsTextInputFieldStyle.SettingsSmallRadius,
        )
    }
}

@Composable
private fun PasswordSection(
    passwordFieldState: TextFieldState,
    isPasswordVisible: Boolean,
    onToggleVisibility: () -> Unit,
    onSubmit: () -> Unit,
    onFocusChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
        SectionHeading(
            title = stringResource(R.string.fast_vault_password_screen_password_hint),
            description = stringResource(R.string.server_backup_password_description),
        )

        VsTextInputField(
            textFieldState = passwordFieldState,
            type =
                VsTextInputFieldType.Password(
                    isVisible = isPasswordVisible,
                    onVisibilityClick = onToggleVisibility,
                ),
            hint = stringResource(R.string.server_backup_password_label),
            onKeyboardAction = { onSubmit() },
            onFocusChanged = onFocusChanged,
            style = VsTextInputFieldStyle.SettingsSmallRadius,
            modifier = modifier,
        )
    }
}

@Composable
private fun ErrorBanner(text: String) {
    val errorColor = Theme.v2.colors.alerts.error
    Row(
        modifier =
            Modifier.fillMaxWidth()
                .clip(BannerShape)
                .background(errorColor.copy(alpha = 0.05f))
                .border(width = 1.dp, shape = BannerShape, color = errorColor.copy(alpha = 0.25f))
                .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_triangle_warning_filled),
            contentDescription = null,
            tint = errorColor,
            modifier = Modifier.size(16.dp),
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
private fun ServerBackupSuccessBottomSheet(onClose: () -> Unit) {
    val context = LocalContext.current

    SettingsModalBottomSheet(onDismissRequest = onClose) {
        Column(
            modifier =
                Modifier.fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 33.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val infiniteTransition = rememberInfiniteTransition(label = "dashes_rotation")
            val dashesRotation by
                infiniteTransition.animateFloat(
                    initialValue = 0f,
                    targetValue = 360f,
                    animationSpec =
                        infiniteRepeatable(
                            animation = tween(durationMillis = 40000, easing = LinearEasing)
                        ),
                    label = "dashes_rotation",
                )

            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = painterResource(R.drawable.il_server_backup_success_dashes),
                    contentDescription = null,
                    modifier = Modifier.matchParentSize().rotate(dashesRotation),
                )
                Image(
                    painter = painterResource(R.drawable.il_server_backup_success),
                    contentDescription = null,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            UiSpacer(83.dp)

            Text(
                text = stringResource(R.string.server_backup_success_title),
                style = Theme.brockmann.headings.title1,
                color = Theme.v2.colors.neutrals.n50,
                textAlign = TextAlign.Center,
            )

            UiSpacer(16.dp)

            Text(
                text = stringResource(R.string.server_backup_success_description),
                style = Theme.brockmann.body.s.medium,
                color = Theme.v2.colors.text.tertiary,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = SENT_TEXT_MAX_WIDTH),
            )

            UiSpacer(20.dp)

            Text(
                text = stringResource(R.string.server_backup_success_check_email),
                style =
                    Theme.brockmann.body.s.medium.copy(textDecoration = TextDecoration.Underline),
                color = Theme.v2.colors.text.secondary,
                modifier =
                    Modifier.clickOnce {
                        val intent =
                            Intent(Intent.ACTION_MAIN).apply {
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

            UiSpacer(84.dp)

            VsButton(
                label = stringResource(R.string.server_backup_success_close),
                variant = VsButtonVariant.Secondary,
                modifier = Modifier.fillMaxWidth(),
                onClick = onClose,
            )

            UiSpacer(28.dp)
        }
    }
}

@Preview
@Composable
private fun ServerBackupScreenPreview() {
    ServerBackupScreen(
        state = ServerBackupUiState(vaultName = "Main Vault", isNameConfirmed = true),
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
    ServerBackupSuccessBottomSheet(onClose = {})
}

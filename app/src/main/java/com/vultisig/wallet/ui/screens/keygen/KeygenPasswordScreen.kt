package com.vultisig.wallet.ui.screens.keygen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.GradientInfoCard
import com.vultisig.wallet.ui.components.MultiColorButton
import com.vultisig.wallet.ui.components.TopBar
import com.vultisig.wallet.ui.components.UiIcon
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.library.form.FormBasicSecureTextField
import com.vultisig.wallet.ui.components.library.form.FormTextFieldCard
import com.vultisig.wallet.ui.models.keygen.KeygenPasswordUiModel
import com.vultisig.wallet.ui.models.keygen.KeygenPasswordViewModel
import com.vultisig.wallet.ui.theme.Theme

@Composable
internal fun KeygenPasswordScreen(
    navController: NavController,
    model: KeygenPasswordViewModel = hiltViewModel(),
) {
    val state by model.state.collectAsState()

    KeygenPasswordScreen(
        navController = navController,
        state = state,
        passwordFieldState = model.passwordFieldState,
        verifyPasswordFieldState = model.verifyPasswordFieldState,
        hintPasswordTextFieldState = model.hintPasswordTextFieldState,
        onPasswordLostFocus = model::verifyPassword,
        onVerifyPasswordLostFocus = model::verifyConfirmPassword,
        onPasswordVisibilityToggle = model::togglePasswordVisibility,
        onVerifyPasswordVisibilityToggle = model::toggleVerifyPasswordVisibility,
        onContinueClick = model::proceed,
    )
}

@Composable
private fun KeygenPasswordScreen(
    navController: NavController,
    state: KeygenPasswordUiModel,
    passwordFieldState: TextFieldState,
    verifyPasswordFieldState: TextFieldState,
    hintPasswordTextFieldState: TextFieldState,
    onPasswordLostFocus: () -> Unit,
    onVerifyPasswordLostFocus: () -> Unit,
    onPasswordVisibilityToggle: () -> Unit,
    onVerifyPasswordVisibilityToggle: () -> Unit,
    onContinueClick: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopBar(
                navController = navController,
                centerText = stringResource(R.string.keygen_password_title),
                startIcon = R.drawable.ic_caret_left,
            )
        },
        content = { contentPadding ->
            Column(
                modifier = Modifier
                    .padding(contentPadding)
                    .background(Theme.colors.oxfordBlue800)
                    .verticalScroll(rememberScrollState())
                    .fillMaxSize()
                    .padding(
                        horizontal = 12.dp,
                        vertical = 16.dp,
                    ),
            ) {
                UiSpacer(size = 8.dp)

                Text(
                    text = stringResource(R.string.keygen_password_caption),
                    style = Theme.montserrat.body1,
                    color = Theme.colors.neutral0
                )

                UiSpacer(size = 12.dp)

                FormBasicSecureTextField(
                    hint = stringResource(R.string.backup_password_screen_enter_password),
                    error = state.passwordError,
                    isObfuscationMode = !state.isPasswordVisible,
                    textFieldState = passwordFieldState,
                    onLostFocus = onPasswordLostFocus,
                    content = {
                        VisibilityToggle(
                            isChecked = state.isPasswordVisible,
                            onClick = onPasswordVisibilityToggle,
                        )
                    },
                )

                UiSpacer(size = 8.dp)

                FormBasicSecureTextField(
                    hint = stringResource(R.string.backup_password_screen_verify_password),
                    error = state.verifyPasswordError,
                    isObfuscationMode = !state.isVerifyPasswordVisible,
                    textFieldState = verifyPasswordFieldState,
                    onLostFocus = onVerifyPasswordLostFocus,
                    content = {
                        VisibilityToggle(
                            isChecked = state.isVerifyPasswordVisible,
                            onClick = onVerifyPasswordVisibilityToggle
                        )
                    },
                )

                UiSpacer(size = 12.dp)

                Text(
                    text = stringResource(R.string.keygen_password_warning_caption),
                    color = Theme.colors.alert,
                    style = Theme.montserrat.body1,
                )

                UiSpacer(size = 12.dp)
                FormTextFieldCard(
                    title = stringResource(
                        R.string.backup_password_optional_password_protection_hint
                    ),
                    hint = stringResource(R.string.backup_password_screen_hint_field),
                    error = state.hintPasswordErrorMessage,
                    keyboardType = KeyboardType.Text,
                    textFieldState = hintPasswordTextFieldState,
                )
            }
        },
        bottomBar = {
            Column(
                Modifier
                    .imePadding()
                    .padding(horizontal = 16.dp)
            ) {
                GradientInfoCard(
                    text = stringResource(R.string.keygen_password_description_caption)
                )

                MultiColorButton(
                    backgroundColor = Theme.colors.turquoise800,
                    textColor = Theme.colors.oxfordBlue800,
                    iconColor = Theme.colors.turquoise800,
                    textStyle = Theme.montserrat.subtitle1,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            start = 16.dp,
                            end = 16.dp,
                            bottom = 16.dp,
                        ),
                    text = stringResource(R.string.keygen_email_continue_button),
                    onClick = onContinueClick,
                )
            }
        },
    )
}

@Composable
private fun VisibilityToggle(
    isChecked: Boolean,
    onClick: () -> Unit,
) {
    IconButton(
        onClick = onClick,
    ) {
        UiIcon(
            drawableResId = if (isChecked)
                R.drawable.hidden
            else R.drawable.visible,
            size = 20.dp,
            contentDescription = "toggle password visibility"
        )
    }
}

@Preview
@Composable
private fun KeygenPasswordScreenPreview() {
    KeygenPasswordScreen(
        navController = rememberNavController(),
        state = KeygenPasswordUiModel(),
        passwordFieldState = TextFieldState(),
        verifyPasswordFieldState = TextFieldState(),
        hintPasswordTextFieldState = TextFieldState(),
        onPasswordLostFocus = {},
        onVerifyPasswordLostFocus = {},
        onPasswordVisibilityToggle = {},
        onVerifyPasswordVisibilityToggle = {},
        onContinueClick = {},
    )
}
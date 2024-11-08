package com.vultisig.wallet.ui.screens.keysign

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.MultiColorButton
import com.vultisig.wallet.ui.components.UiIcon
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.library.form.FormBasicSecureTextField
import com.vultisig.wallet.ui.models.keysign.KeysignPasswordUiModel
import com.vultisig.wallet.ui.models.keysign.KeysignPasswordViewModel
import com.vultisig.wallet.ui.theme.Theme
import com.vultisig.wallet.ui.utils.UiText
import com.vultisig.wallet.ui.utils.asString

@Composable
internal fun KeysignPasswordScreen(
    model: KeysignPasswordViewModel = hiltViewModel(),
) {
    val state by model.state.collectAsState()

    KeysignPasswordScreen(
        state = state,
        passwordFieldState = model.passwordFieldState,
        onPasswordLostFocus = model::verifyPassword,
        onPasswordVisibilityToggle = model::togglePasswordVisibility,
        onContinueClick = model::proceed,
    )
}

@Composable
private fun KeysignPasswordScreen(
    state: KeysignPasswordUiModel,
    passwordFieldState: TextFieldState,
    onPasswordLostFocus: () -> Unit,
    onPasswordVisibilityToggle: () -> Unit,
    onContinueClick: () -> Unit,
) {
    Scaffold(
        content = { contentPadding ->
            Column(
                modifier = Modifier
                    .padding(contentPadding)
                    .background(Theme.colors.oxfordBlue800)
                    .fillMaxSize()
                    .padding(
                        horizontal = 12.dp,
                        vertical = 16.dp,
                    ),
            ) {
                UiSpacer(size = 8.dp)

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

                UiSpacer(size = 12.dp)

                if (state.passwordHint != null) {
                    UiSpacer(size = 8.dp)
                    Text(
                        modifier = Modifier.fillMaxWidth(),
                        text = state.passwordHint.asString(),
                        color = Theme.colors.neutral0,
                        style = Theme.menlo.body2,
                    )
                }
            }
        },
        bottomBar = {
            Column(
                Modifier
                    .imePadding()
                    .padding(horizontal = 16.dp)
            ) {
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
private fun KeysignPasswordScreenPreview() {
    KeysignPasswordScreen(
        state = KeysignPasswordUiModel(),
        passwordFieldState = TextFieldState(),
        onPasswordLostFocus = {},
        onPasswordVisibilityToggle = {},
        onContinueClick = {},
    )
}

@Preview
@Composable
private fun KeysignPasswordWithHintScreenPreview() {
    KeysignPasswordScreen(
        state = KeysignPasswordUiModel(passwordHint = UiText.DynamicString("Hint")),
        passwordFieldState = TextFieldState(),
        onPasswordLostFocus = {},
        onPasswordVisibilityToggle = {},
        onContinueClick = {},
    )
}
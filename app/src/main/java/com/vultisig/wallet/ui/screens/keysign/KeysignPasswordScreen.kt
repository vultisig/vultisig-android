package com.vultisig.wallet.ui.screens.keysign

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.buttons.VsButton
import com.vultisig.wallet.ui.components.inputs.VsTextInputField
import com.vultisig.wallet.ui.components.inputs.VsTextInputFieldInnerState
import com.vultisig.wallet.ui.components.inputs.VsTextInputFieldType
import com.vultisig.wallet.ui.components.topbar.VsTopAppBar
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

    InputPasswordScreen(
        state = state,
        subtitle = null,
        passwordFieldState = model.passwordFieldState,
        onPasswordVisibilityToggle = model::togglePasswordVisibility,
        onContinueClick = model::proceed,
        onBackClick = model::back,
    )
}

@Composable
internal fun InputPasswordScreen(
    state: KeysignPasswordUiModel,
    subtitle: String?,
    passwordFieldState: TextFieldState,
    onPasswordVisibilityToggle: () -> Unit,
    onContinueClick: () -> Unit,
    onBackClick: () -> Unit,
) {
    Scaffold(
        containerColor = Theme.v2.colors.backgrounds.primary,
        topBar = {
            VsTopAppBar(
                onBackClick = onBackClick,
            )
        },
        content = { contentPadding ->
            Column(
                modifier = Modifier
                    .padding(contentPadding)
                    .padding(
                        horizontal = 24.dp,
                        vertical = 12.dp,
                    ),
            ) {
                Text(
                    text = stringResource(R.string.keysign_password_enter_your_password),
                    color = Theme.v2.colors.text.primary,
                    style = Theme.brockmann.headings.largeTitle,
                )

                UiSpacer(16.dp)

                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        color = Theme.v2.colors.text.extraLight,
                        style = Theme.brockmann.body.s.medium,
                    )
                }

                UiSpacer(16.dp)

                Column(
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .weight(1f),
                ) {
                    val focusRequester = remember { FocusRequester() }

                    LaunchedEffect(Unit) {
                        focusRequester.requestFocus()
                    }

                    VsTextInputField(
                        textFieldState = passwordFieldState,
                        hint = stringResource(R.string.backup_password_screen_enter_password),
                        type = VsTextInputFieldType.Password(
                            isVisible = state.isPasswordVisible,
                            onVisibilityClick = onPasswordVisibilityToggle,
                        ),
                        focusRequester = focusRequester,
                        imeAction = ImeAction.Go,
                        onKeyboardAction = {
                            onContinueClick()
                        },
                        innerState = if (state.passwordError != null)
                            VsTextInputFieldInnerState.Error
                        else VsTextInputFieldInnerState.Default,
                        footNote = state.passwordError?.asString(),
                        modifier = Modifier
                            .testTag("InputPasswordScreen.password")
                    )

                    UiSpacer(size = 12.dp)

                    if (state.passwordHint != null) {
                        Text(
                            text = state.passwordHint.asString(),
                            color = Theme.v2.colors.text.light,
                            style = Theme.brockmann.supplementary.footnote,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        },
        bottomBar = {
            VsButton(
                label = stringResource(R.string.keygen_email_continue_button),
                onClick = onContinueClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        all = 24.dp,
                    )
                    .testTag("InputPasswordScreen.next")
            )
        },
    )
}

@Preview
@Composable
private fun KeysignPasswordScreenPreview() {
    InputPasswordScreen(
        subtitle = "Enter your password to unlock your Server Share and start the upgrade",
        state = KeysignPasswordUiModel(passwordHint = UiText.DynamicString("Hint")),
        passwordFieldState = TextFieldState(),
        onPasswordVisibilityToggle = {},
        onContinueClick = {},
        onBackClick = {},
    )
}
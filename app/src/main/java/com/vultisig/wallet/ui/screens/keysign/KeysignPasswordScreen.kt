package com.vultisig.wallet.ui.screens.keysign

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.UiIcon
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.buttons.VsButton
import com.vultisig.wallet.ui.components.inputs.VsTextInputField
import com.vultisig.wallet.ui.components.inputs.VsTextInputFieldInnerState
import com.vultisig.wallet.ui.components.inputs.VsTextInputFieldType
import com.vultisig.wallet.ui.components.bottomsheet.VsBottomSheet
import com.vultisig.wallet.ui.models.keysign.KeysignPasswordUiModel
import com.vultisig.wallet.ui.models.keysign.KeysignPasswordViewModel
import com.vultisig.wallet.ui.screens.send.FadingHorizontalDivider
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun InputPasswordScreen(
    state: KeysignPasswordUiModel,
    subtitle: String?,
    passwordFieldState: TextFieldState,
    onPasswordVisibilityToggle: () -> Unit,
    onContinueClick: () -> Unit,
    onBackClick: () -> Unit,
) {

    KeysignPasswordBottomSheet(
        state = state,
        subtitle = subtitle,
        passwordFieldState = passwordFieldState,
        onPasswordVisibilityToggle = onPasswordVisibilityToggle,
        onContinueClick = onContinueClick,
        onBackClick = onBackClick,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KeysignPasswordBottomSheet(
    state: KeysignPasswordUiModel,
    title: String? = stringResource(R.string.keysign_password_enter_your_password),
    subtitle: String?,
    confirmButtonLabel: String = stringResource(R.string.keygen_email_continue_button),
    passwordFieldState: TextFieldState,
    onPasswordVisibilityToggle: () -> Unit,
    onContinueClick: () -> Unit,
    onBackClick: () -> Unit,
) {
    VsBottomSheet(
        onDismissRequest = onBackClick,
    ) {
        KeysignPasswordSheetContent(
            modifier = Modifier
                .padding(
                    horizontal = 16.dp,
                    vertical = 12.dp,
                ),
            title = title,
            subtitle = subtitle,
            confirmButtonLabel = confirmButtonLabel,
            state = state,
            passwordFieldState = passwordFieldState,
            onPasswordVisibilityToggle = onPasswordVisibilityToggle,
            onContinueClick = onContinueClick,
            onBackClick = onBackClick,
        )
    }

}


@Composable
fun KeysignPasswordSheetContent(
    modifier: Modifier = Modifier,
    title: String?,
    subtitle: String?,
    confirmButtonLabel: String = stringResource(R.string.keygen_email_continue_button),
    state: KeysignPasswordUiModel,
    passwordFieldState: TextFieldState,
    onPasswordVisibilityToggle: () -> Unit,
    onContinueClick: () -> Unit,
    onBackClick: () -> Unit,
) {
    Column(modifier = modifier) {
        UiSpacer(
            size = 32.dp
        )

        UiIcon(
            drawableResId = R.drawable.focus_lock,
            size = 24.dp,
            modifier = Modifier
                .align(alignment = Alignment.CenterHorizontally),
            tint = Theme.colors.primary.accent4,
        )

        UiSpacer(
            size = 20.dp
        )

        if (title != null) {
            Text(
                text = title,
                color = Theme.colors.text.primary,
                style = Theme.brockmann.headings.title3,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
            UiSpacer(20.dp)
        }

        FadingHorizontalDivider()
        UiSpacer(
            size = 20.dp
        )

        if (subtitle != null) {
            Text(
                text = subtitle,
                color = Theme.colors.text.extraLight,
                style = Theme.brockmann.supplementary.caption,
                modifier = Modifier
                    .width(211.dp)
                    .align(Alignment.CenterHorizontally),
                textAlign = TextAlign.Center,
            )
            UiSpacer(16.dp)
        }

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
            invisibleIcon = R.drawable.eye_closed,
            modifier = Modifier
                .testTag("InputPasswordScreen.password")
        )

        UiSpacer(size = 10.dp)

        if (state.passwordHint != null) {

            var isHintVisible by remember {
                mutableStateOf(false)
            }

            val caretRotationDegree by animateFloatAsState(if (isHintVisible) -90f else 90f)

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = {
                        isHintVisible = !isHintVisible
                    })
                    .wrapContentWidth(align = Alignment.Start),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (isHintVisible) stringResource(R.string.keysign_password_hide_hint) else stringResource(
                        R.string.keysign_password_show_hint
                    ),
                    color = Theme.colors.text.light,
                    style = Theme.brockmann.supplementary.footnote,
                )

                UiSpacer(
                    size = 4.dp
                )

                UiIcon(
                    drawableResId = R.drawable.ic_small_caret_right,
                    modifier = Modifier
                        .rotate(degrees = caretRotationDegree),
                    size = 12.dp,
                    tint = Theme.colors.text.light,
                )
            }

            AnimatedVisibility(visible = isHintVisible) {
                Column {
                    UiSpacer(
                        size = 8.dp
                    )
                    Text(
                        text = state.passwordHint.asString(),
                        color = Theme.colors.text.light,
                        style = Theme.brockmann.supplementary.footnote,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            UiSpacer(size = 12.dp)
        }

        VsButton(
            label = confirmButtonLabel,
            onClick = onContinueClick,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("InputPasswordScreen.next")
        )
    }
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
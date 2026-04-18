package com.vultisig.wallet.ui.screens.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.UiIcon
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.bottomsheet.VsModalBottomSheet
import com.vultisig.wallet.ui.components.buttons.VsButton
import com.vultisig.wallet.ui.components.inputs.VsTextInputField
import com.vultisig.wallet.ui.components.inputs.VsTextInputFieldInnerState
import com.vultisig.wallet.ui.components.inputs.VsTextInputFieldType
import com.vultisig.wallet.ui.theme.Theme
import com.vultisig.wallet.ui.utils.UiText
import com.vultisig.wallet.ui.utils.asString

@Composable
internal fun FastVaultPasswordReminderDialog(
    model: FastVaultPasswordReminderViewModel = hiltViewModel()
) {
    val state by model.state.collectAsState()

    VsModalBottomSheet(
        onDismissRequest = model::back,
        content = {
            FastVaultPasswordReminderDialog(
                state = state,
                passwordFieldState = model.passwordFieldState,
                onVerifyClick = model::verify,
                onPasswordVisibilityClick = model::togglePasswordVisibility,
            )
        },
    )
}

@Composable
private fun FastVaultPasswordReminderDialog(
    state: FastVaultPasswordReminderUiModel,
    passwordFieldState: TextFieldState,
    onVerifyClick: () -> Unit,
    onPasswordVisibilityClick: () -> Unit,
) {
    Column(
        modifier =
            Modifier.background(Theme.v2.colors.backgrounds.primary)
                .fillMaxWidth()
                .padding(all = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        UiSpacer(size = 18.dp)

        UiIcon(
            drawableResId = R.drawable.focus_lock,
            size = 32.dp,
            tint = Theme.v2.colors.primary.accent4,
        )

        UiSpacer(size = 20.dp)

        Text(
            text = stringResource(R.string.fast_vault_password_reminder_title),
            textAlign = TextAlign.Center,
            style = Theme.brockmann.headings.title3,
            color = Theme.v2.colors.text.primary,
        )

        UiSpacer(size = 12.dp)

        Text(
            text = state.vaultName,
            textAlign = TextAlign.Center,
            style = Theme.brockmann.headings.title2,
            color = Theme.v2.colors.text.primary,
        )

        UiSpacer(size = 20.dp)

        Text(
            text = stringResource(R.string.periodically_ask_verify_password),
            textAlign = TextAlign.Center,
            style = Theme.brockmann.supplementary.caption,
            color = Theme.v2.colors.text.tertiary,
        )

        UiSpacer(size = 10.dp)

        VsTextInputField(
            textFieldState = passwordFieldState,
            hint = stringResource(R.string.backup_password_screen_enter_password),
            type =
                VsTextInputFieldType.Password(
                    isVisible = state.isPasswordVisible,
                    onVisibilityClick = onPasswordVisibilityClick,
                ),
            imeAction = ImeAction.Go,
            onKeyboardAction = { onVerifyClick() },
            innerState =
                if (state.error != null) VsTextInputFieldInnerState.Error
                else VsTextInputFieldInnerState.Default,
            footNote = state.error?.asString(),
        )

        if (state.passwordHint != null) {
            UiSpacer(size = 10.dp)

            var isHintVisible by remember { mutableStateOf(false) }

            val caretRotationDegree by animateFloatAsState(if (isHintVisible) -90f else 90f)

            Row(
                modifier =
                    Modifier.fillMaxWidth()
                        .clickable(onClick = { isHintVisible = !isHintVisible })
                        .wrapContentWidth(align = Alignment.Start),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text =
                        if (isHintVisible) stringResource(R.string.keysign_password_hide_hint)
                        else stringResource(R.string.keysign_password_show_hint),
                    color = Theme.v2.colors.text.secondary,
                    style = Theme.brockmann.supplementary.footnote,
                )

                UiSpacer(size = 4.dp)

                UiIcon(
                    drawableResId = R.drawable.ic_small_caret_right,
                    modifier = Modifier.rotate(degrees = caretRotationDegree),
                    size = 12.dp,
                    tint = Theme.v2.colors.text.secondary,
                )
            }

            AnimatedVisibility(visible = isHintVisible) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    UiSpacer(size = 8.dp)
                    Text(
                        text = state.passwordHint.asString(),
                        color = Theme.v2.colors.text.secondary,
                        style = Theme.brockmann.supplementary.footnote,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }

        UiSpacer(size = 10.dp)

        VsButton(
            label = stringResource(R.string.fast_vault_password_reminder_done_button),
            onClick = onVerifyClick,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
@Preview
private fun FastVaultPasswordReminderDialogPreview1() {
    FastVaultPasswordReminderDialog(
        state =
            FastVaultPasswordReminderUiModel(
                vaultName = "Main Vault",
                passwordHint = UiText.DynamicString("Hint: favorite color"),
            ),
        passwordFieldState = TextFieldState(),
        onVerifyClick = {},
        onPasswordVisibilityClick = {},
    )
}

@Composable
@Preview
private fun FastVaultPasswordReminderDialogPreview2() {
    FastVaultPasswordReminderDialog(
        state = FastVaultPasswordReminderUiModel(vaultName = "Main Vault"),
        passwordFieldState = TextFieldState(),
        onVerifyClick = {},
        onPasswordVisibilityClick = {},
    )
}

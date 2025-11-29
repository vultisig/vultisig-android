package com.vultisig.wallet.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.bottomsheet.VsModalBottomSheet
import com.vultisig.wallet.ui.components.buttons.VsButton
import com.vultisig.wallet.ui.components.inputs.VsTextInputField
import com.vultisig.wallet.ui.components.inputs.VsTextInputFieldInnerState
import com.vultisig.wallet.ui.components.inputs.VsTextInputFieldType
import com.vultisig.wallet.ui.screens.send.FadingHorizontalDivider
import com.vultisig.wallet.ui.theme.Theme
import com.vultisig.wallet.ui.utils.asString

@Composable
internal fun FastVaultPasswordReminderDialog(
    model: FastVaultPasswordReminderViewModel = hiltViewModel(),
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
        }
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
        modifier = Modifier
            .background(Theme.v2.colors.backgrounds.primary)
            .fillMaxWidth()
            .padding(all = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.verify_your_server_share_password),
            textAlign = TextAlign.Center,
            style = Theme.brockmann.headings.title3,
            color = Theme.v2.colors.text.primary,
            modifier = Modifier
                .padding(all = 10.dp),
        )

        FadingHorizontalDivider()

        Text(
            text = stringResource(R.string.periodically_ask_verify_password),
            textAlign = TextAlign.Center,
            style = Theme.brockmann.supplementary.caption,
            color = Theme.v2.colors.text.extraLight,
            modifier = Modifier
        )

        VsTextInputField(
            textFieldState = passwordFieldState,
            hint = stringResource(R.string.keysign_password_title),
            type = VsTextInputFieldType.Password(
                isVisible = state.isPasswordVisible,
                onVisibilityClick = onPasswordVisibilityClick,
            ),
            imeAction = ImeAction.Go,
            onKeyboardAction = {
                onVerifyClick()
            },
            innerState = if (state.error != null)
                VsTextInputFieldInnerState.Error
            else VsTextInputFieldInnerState.Default,
            footNote = state.error?.asString(),
        )

        VsButton(
            label = stringResource(R.string.verify_transaction_screen_title),
            onClick = onVerifyClick,
            modifier = Modifier
                .fillMaxWidth(),
        )
    }

}

@Composable
@Preview
private fun FastVaultPasswordReminderDialogPreview() {
    FastVaultPasswordReminderDialog(
        state = FastVaultPasswordReminderUiModel(),
        passwordFieldState = TextFieldState(),
        onVerifyClick = {},
        onPasswordVisibilityClick = {},
    )
}
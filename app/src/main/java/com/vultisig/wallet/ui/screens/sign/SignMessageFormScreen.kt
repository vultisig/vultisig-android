package com.vultisig.wallet.ui.screens.sign

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vultisig.wallet.R
import com.vultisig.wallet.data.models.VaultId
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.buttons.VsButton
import com.vultisig.wallet.ui.components.buttons.VsButtonState
import com.vultisig.wallet.ui.components.inputs.VsTextInputField
import com.vultisig.wallet.ui.models.sign.SignMessageFormViewModel


@Composable
internal fun SignMessageFormScreen(
    vaultId: VaultId,
    model: SignMessageFormViewModel = hiltViewModel(),
) {
    val state by model.state.collectAsState()

    LaunchedEffect(vaultId) {
        model.setData(vaultId)
    }

    val focusManager = LocalFocusManager.current
    val isFormValid = model.methodFieldState.text.isNotBlank() &&
            model.messageFieldState.text.isNotBlank()

    SignMessageFormScreen(
        methodFieldState = model.methodFieldState,
        messageFieldState = model.messageFieldState,
        state = if (state.isLoading || !isFormValid)
            VsButtonState.Disabled
        else
            VsButtonState.Enabled,
        onSign = {
            focusManager.clearFocus()
            model.sign()
        }
    )
}


@Composable
private fun SignMessageFormScreen(
    methodFieldState: TextFieldState,
    messageFieldState: TextFieldState,
    state: VsButtonState,
    onSign: () -> Unit = {},
) {
    Scaffold(
        bottomBar = {
            VsButton(
                label = stringResource(id = R.string.sign_message_continue),
                state = state,
                onClick = onSign,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(all = 16.dp),
            )
        }
    ) {
        Column(
            modifier = Modifier
                .padding(it)
                .padding(
                    horizontal = 16.dp,
                    vertical = 12.dp
                )
        ) {
            VsTextInputField(
                textFieldState = methodFieldState,
                hint = stringResource(id = R.string.hint_signing_method),
                keyboardType = KeyboardType.Text,
                autoCorrectEnabled = false,
            )

            UiSpacer(14.dp)

            VsTextInputField(
                textFieldState = messageFieldState,
                hint = stringResource(id = R.string.hint_message_to_sign),
                keyboardType = KeyboardType.Text,
                autoCorrectEnabled = false,
            )
        }
    }
}


@Preview
@Composable
private fun SignMessageFormScreenPreview() {
    SignMessageFormScreen(
        messageFieldState = rememberTextFieldState(),
        methodFieldState = rememberTextFieldState(),
        state = VsButtonState.Enabled,
    )
}
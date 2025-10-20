package com.vultisig.wallet.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.buttons.VsButton
import com.vultisig.wallet.ui.components.buttons.VsButtonState
import com.vultisig.wallet.ui.components.inputs.VsTextInputField
import com.vultisig.wallet.ui.components.inputs.VsTextInputFieldInnerState
import com.vultisig.wallet.ui.components.v2.scaffold.V2Scaffold
import com.vultisig.wallet.ui.models.VaultRenameViewModel
import com.vultisig.wallet.ui.utils.asString


@Composable
internal fun VaultRenameScreen(
    viewModel: VaultRenameViewModel = hiltViewModel(),
) {

    LaunchedEffect(key1 = Unit) {
        viewModel.loadData()
    }

    val uiState by viewModel.uiState.collectAsState()

    VaultRenameScreen(
        onSaveClick = viewModel::saveName,
        onBackClick = viewModel::back,
        textFieldState = viewModel.renameTextFieldState,
        isLoading = uiState.isLoading,
        errorText = uiState.errorMessage?.asString(),
        onCloseClick = viewModel::clearText
    )
}


@Composable
private fun VaultRenameScreen(
    onSaveClick: () -> Unit,
    onBackClick: () -> Unit,
    onCloseClick: () -> Unit,
    textFieldState: TextFieldState,
    isLoading: Boolean,
    errorText: String?,
) {
    V2Scaffold(
        title = stringResource(id = R.string.rename_vault_screen_rename_vault),
        onBackClick = onBackClick
    ) {
        Column {
            VsTextInputField(
                modifier = Modifier.padding(
                    vertical = 24.dp
                ),
                hint = stringResource(id = R.string.rename_vault_screen_vault_name),
                textFieldState = textFieldState,
                footNote = errorText,
                trailingIcon = R.drawable.close_circle,
                onTrailingIconClick = onCloseClick,
                innerState = VsTextInputFieldInnerState.Error.takeIf { errorText != null }
                    ?: VsTextInputFieldInnerState.Default,
            )
            UiSpacer(
                weight = 1f
            )
            VsButton(
                label = stringResource(id = R.string.add_vault_save),
                modifier = Modifier.fillMaxWidth(),
                onClick = onSaveClick,
                state = VsButtonState.Disabled.takeIf { isLoading }
                    ?: VsButtonState.Enabled,
            )
        }

    }
}

@Preview
@Composable
private fun VaultRenameScreenPreview() {
    VaultRenameScreen(
        onSaveClick = {},
        onBackClick = {},
        onCloseClick = {},
        textFieldState = rememberTextFieldState(),
        isLoading = false,
        errorText = null,
    )
}
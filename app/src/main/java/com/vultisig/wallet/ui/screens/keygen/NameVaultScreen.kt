package com.vultisig.wallet.ui.screens.keygen

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
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
import com.vultisig.wallet.ui.components.buttons.VsButtonState
import com.vultisig.wallet.ui.components.inputs.VsTextInputField
import com.vultisig.wallet.ui.components.topbar.VsTopAppBar
import com.vultisig.wallet.ui.models.keygen.NameVaultUiModel
import com.vultisig.wallet.ui.models.keygen.NameVaultViewModel
import com.vultisig.wallet.ui.theme.Theme
import com.vultisig.wallet.ui.utils.UiText
import com.vultisig.wallet.ui.utils.asString

@Composable
internal fun NameVaultScreen(
    model: NameVaultViewModel = hiltViewModel(),
) {
    val state by model.state.collectAsState()

    NameVaultScreen(
        state = state,
        textFieldState = model.nameFieldState,
        onNextClick = model::navigateToEmail,
        onClearClick = model::clearInput,
        onBackClick = model::back
    )
}

@Composable
private fun NameVaultScreen(
    state: NameVaultUiModel,
    textFieldState: TextFieldState,
    onNextClick: () -> Unit,
    onClearClick: () -> Unit,
    onBackClick: () -> Unit,
) {
    Scaffold(
        containerColor = Theme.v2.colors.backgrounds.primary,
        topBar = {
            VsTopAppBar(
                onBackClick = onBackClick
            )
        },
        bottomBar = {
            VsButton(
                label = stringResource(R.string.fast_vault_name_screen_next),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
                    .testTag("NameVaultScreen.continue"),
                state = if (state.isNextButtonEnabled)
                    VsButtonState.Enabled else VsButtonState.Disabled,
                onClick = onNextClick,
            )
        }
    ) {
        val focusRequester = remember {
            FocusRequester()
        }
        LaunchedEffect(Unit) {
            focusRequester.requestFocus()
        }
        Column(
            Modifier
                .padding(it)
                .padding(
                    top = 12.dp,
                    start = 24.dp,
                    end = 24.dp,
                )
        ) {
            Text(
                text = stringResource(R.string.fast_vault_name_screen_title),
                style = Theme.brockmann.headings.largeTitle,
                color = Theme.v2.colors.text.primary,
            )
            UiSpacer(16.dp)
            Text(
                text = stringResource(R.string.fast_vault_name_screen_desc),
                style = Theme.brockmann.body.s.medium,
                color = Theme.v2.colors.text.extraLight
            )
            VsTextInputField(
                textFieldState = textFieldState,
                trailingIcon = R.drawable.close_circle,
                onTrailingIconClick = onClearClick,
                focusRequester = focusRequester,
                footNote = state.errorMessage?.asString(),
                imeAction = ImeAction.Go,
                onKeyboardAction = {
                    onNextClick()
                },
                modifier = Modifier
                    .fillMaxSize()
                    .wrapContentHeight()
                    .testTag("NameVaultScreen.nameField")
            )
        }
    }
}


@Preview
@Composable
private fun FastVaultNameScreenPreview() {
    NameVaultScreen(
        state = NameVaultUiModel(),
        textFieldState = rememberTextFieldState(),
        onNextClick = {},
        onClearClick = {},
        onBackClick = {}
    )
}

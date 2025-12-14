package com.vultisig.wallet.ui.screens.keygen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
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
import com.vultisig.wallet.ui.components.buttons.VsButtonVariant
import com.vultisig.wallet.ui.components.inputs.VsTextInputField
import com.vultisig.wallet.ui.components.inputs.VsTextInputFieldType
import com.vultisig.wallet.ui.components.topbar.VsTopAppBar
import com.vultisig.wallet.ui.models.keygen.FastVaultPasswordHintUiModel
import com.vultisig.wallet.ui.models.keygen.FastVaultPasswordHintViewModel
import com.vultisig.wallet.ui.theme.Theme
import com.vultisig.wallet.ui.utils.asString

@Composable
internal fun FastVaultPasswordHintScreen(
    model: FastVaultPasswordHintViewModel = hiltViewModel(),
) {
    val state by model.state.collectAsState()

    FastVaultPasswordHintScreen(
        state = state,
        textFieldState = model.passwordHintFieldState,
        onNextClick = model::next,
        onSkipClick = model::skip,
        onBackClick = model::back
    )
}

@Composable
private fun FastVaultPasswordHintScreen(
    state: FastVaultPasswordHintUiModel,
    textFieldState: TextFieldState,
    onNextClick: () -> Unit,
    onSkipClick: () -> Unit,
    onBackClick: () -> Unit,
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    Scaffold(
        containerColor = Theme.v2.colors.backgrounds.primary,
        topBar = {
            VsTopAppBar(
                onBackClick = onBackClick
            )
        },
        bottomBar = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .padding(24.dp),
            ) {
                VsButton(
                    label = stringResource(R.string.fast_vault_password_hint_skip),
                    onClick = onSkipClick,
                    variant = VsButtonVariant.Secondary,
                    modifier = Modifier.weight(1f)
                        .testTag("FastVaultPasswordHintScreen.skip")
                )

                VsButton(
                    label = stringResource(R.string.fast_vault_password_hint_next),
                    state = if (state.isNextAvailable)
                        VsButtonState.Enabled
                    else VsButtonState.Disabled,
                    onClick = {
                        keyboardController?.hide()
                        onNextClick()
                    },
                    modifier = Modifier.weight(1f),
                )
            }
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
                text = stringResource(R.string.fast_vault_password_hint_screen_title),
                style = Theme.brockmann.headings.largeTitle,
                color = Theme.v2.colors.text.primary,
            )
            UiSpacer(16.dp)
            Text(
                text = stringResource(R.string.fast_vault_password_hint_screen_desc),
                style = Theme.brockmann.body.s.medium,
                color = Theme.v2.colors.text.extraLight
            )
            VsTextInputField(
                textFieldState = textFieldState,
                hint = stringResource(R.string.fast_vault_password_hint_screen_hint),
                footNote = state.errorMessage?.asString(),
                trailingIcon = R.drawable.ic_question_mark,
                focusRequester = focusRequester,
                type = VsTextInputFieldType.MultiLine(5),
                imeAction = ImeAction.Go,
                onKeyboardAction = {
                    keyboardController?.hide()
                    onNextClick()
                },
                modifier = Modifier
                    .padding(top = 16.dp)
            )
        }
    }
}


@Preview
@Composable
private fun FastVaultPasswordHintScreenPreview() {
    FastVaultPasswordHintScreen(
        state = FastVaultPasswordHintUiModel(),
        textFieldState = rememberTextFieldState(),
        onNextClick = {},
        onBackClick = {},
        onSkipClick = {},
    )
}

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
import com.vultisig.wallet.ui.components.inputs.VsTextInputFieldInnerState
import com.vultisig.wallet.ui.components.topbar.VsTopAppBar
import com.vultisig.wallet.ui.models.keygen.FastVaultEmailState
import com.vultisig.wallet.ui.models.keygen.FastVaultEmailViewModel
import com.vultisig.wallet.ui.theme.Theme
import com.vultisig.wallet.ui.utils.asString

@Composable
internal fun FastVaultEmailScreen(
    model: FastVaultEmailViewModel = hiltViewModel(),
) {
    val state by model.state.collectAsState()

    FastVaultEmailScreen(
        state = state,
        textFieldState = model.emailFieldState,
        onNextClick = model::navigateToPassword,
        onClearClick = model::clearInput,
        onBackClick = model::back
    )
}

@Composable
private fun FastVaultEmailScreen(
    state: FastVaultEmailState,
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
                label = stringResource(R.string.enter_email_screen_next),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
                    .testTag("FastVaultEmailScreen.next"),
                onClick = onNextClick,
                state = if (state.innerState == VsTextInputFieldInnerState.Success)
                    VsButtonState.Enabled else VsButtonState.Disabled
            )
        }
    ) {
        Column(
            Modifier
                .padding(it)
                .padding(
                    top = 12.dp,
                    start = 24.dp,
                    end = 24.dp,
                )
        ) {
            val focusRequester = remember {
                FocusRequester()
            }
            LaunchedEffect(Unit) {
                focusRequester.requestFocus()
            }
            Text(
                text = stringResource(R.string.enter_email_screen_title),
                style = Theme.brockmann.headings.largeTitle,
                color = Theme.v2.colors.text.primary,
            )
            UiSpacer(16.dp)
            Text(
                text = stringResource(R.string.enter_email_screen_desc),
                style = Theme.brockmann.body.s.medium,
                color = Theme.v2.colors.text.extraLight
            )
            VsTextInputField(
                textFieldState = textFieldState,
                innerState = state.innerState,
                hint = stringResource(R.string.enter_email_screen_hint),
                trailingIcon = R.drawable.close_circle,
                onTrailingIconClick = onClearClick,
                footNote = state.errorMessage?.asString(),
                focusRequester = focusRequester,
                imeAction = ImeAction.Go,
                onKeyboardAction = {
                    onNextClick()
                },
                modifier = Modifier
                    .fillMaxSize()
                    .wrapContentHeight()
                    .testTag("FastVaultEmailScreen.emailField")
            )
        }
    }
}


@Preview
@Composable
private fun FastVaultEmailScreenPreview() {
    FastVaultEmailScreen(
        state = FastVaultEmailState(),
        textFieldState = rememberTextFieldState(),
        onNextClick = {},
        onClearClick = {},
        onBackClick = {}
    )
}

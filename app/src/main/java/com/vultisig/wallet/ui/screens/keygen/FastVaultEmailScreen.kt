package com.vultisig.wallet.ui.screens.keygen

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
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
        onFocusChange = model::updateInputFocus,
        onNextClick = model::navigateToPassword,
        onClearClick = model::clearInput,
        onBackClick = model::navigateToBack
    )
}

@Composable
private fun FastVaultEmailScreen(
    state: FastVaultEmailState,
    onFocusChange: (Boolean) -> Unit,
    onNextClick: () -> Unit,
    onClearClick: () -> Unit,
    onBackClick: () -> Unit,
) {
    Scaffold(
        containerColor = Theme.colors.backgrounds.primary,
        topBar = {
            VsTopAppBar(
                onBackClick = onBackClick
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
                    bottom = 24.dp
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
                color = Theme.colors.text.primary,
            )
            UiSpacer(16.dp)
            Text(
                text = stringResource(R.string.enter_email_screen_desc),
                style = Theme.brockmann.body.s.medium,
                color = Theme.colors.text.extraLight
            )
            VsTextInputField(
                textFieldState = state.textFieldState,
                onFocusChanged = onFocusChange,
                focused = state.isFocused,
                innerState = state.innerState,
                trailingIcon = R.drawable.close_circle,
                onTrailingIconClick = onClearClick,
                footNote = state.errorMessage?.asString(),
                focusRequester = focusRequester,
                modifier = Modifier
                    .weight(1f)
                    .wrapContentHeight()
            )
            VsButton(
                label = "Next",
                modifier = Modifier.fillMaxWidth(),
                onClick = onNextClick,
                state = if (state.innerState == VsTextInputFieldInnerState.Success)
                    VsButtonState.Enabled else VsButtonState.Disabled
            )
        }
    }
}


@Preview
@Composable
private fun FastVaultEmailScreenPreview() {
    FastVaultEmailScreen(
        state = FastVaultEmailState(),
        onFocusChange = {},
        onNextClick = {},
        onClearClick = {},
        onBackClick = {}
    )
}

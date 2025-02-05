package com.vultisig.wallet.ui.screens.keygen

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.buttons.VsButton
import com.vultisig.wallet.ui.components.buttons.VsButtonVariant
import com.vultisig.wallet.ui.components.inputs.VsTextInputField
import com.vultisig.wallet.ui.components.inputs.VsTextInputFieldType
import com.vultisig.wallet.ui.components.topbar.VsTopAppBar
import com.vultisig.wallet.ui.models.keygen.FastVaultPasswordHintState
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
        onNextClick = {

        },
        onSkipClick = {

        },
        onBackClick = model::back
    )
}

@Composable
private fun FastVaultPasswordHintScreen(
    state: FastVaultPasswordHintState,
    textFieldState: TextFieldState,
    onNextClick: () -> Unit,
    onSkipClick: () -> Unit,
    onBackClick: () -> Unit,
) {
    Scaffold(
        containerColor = Theme.colors.backgrounds.primary,
        topBar = {
            VsTopAppBar(
                onBackClick = onBackClick
            )
        },
        bottomBar = {
            Row(Modifier.padding(24.dp)) {
                VsButton(
                    label = stringResource(R.string.fast_vault_password_hint_skip),
                    modifier = Modifier.weight(1f),
                    onClick = onSkipClick,
                    variant = VsButtonVariant.Secondary
                )
                UiSpacer(8.dp)
                VsButton(
                    label = stringResource(R.string.fast_vault_password_hint_next),
                    modifier = Modifier.weight(1f),
                    onClick = onNextClick,
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
                color = Theme.colors.text.primary,
            )
            UiSpacer(16.dp)
            Text(
                text = stringResource(R.string.fast_vault_password_hint_screen_desc),
                style = Theme.brockmann.body.s.medium,
                color = Theme.colors.text.extraLight
            )
            VsTextInputField(
                textFieldState = textFieldState,
                hint = stringResource(R.string.fast_vault_password_hint_screen_hint),
                footNote = state.errorMessage?.asString(),
                trailingIcon = R.drawable.ic_question_mark,
                focusRequester = focusRequester,
                type = VsTextInputFieldType.MultiLine(5),
                modifier = Modifier
                    .fillMaxSize()
                    .wrapContentHeight()
            )
        }
    }
}


@Preview
@Composable
private fun FastVaultPasswordHintScreenPreview() {
    FastVaultPasswordHintScreen(
        state = FastVaultPasswordHintState(),
        textFieldState = rememberTextFieldState(),
        onNextClick = {},
        onBackClick = {},
        onSkipClick = {},
    )
}

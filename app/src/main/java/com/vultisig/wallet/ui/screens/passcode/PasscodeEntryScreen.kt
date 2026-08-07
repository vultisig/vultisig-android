package com.vultisig.wallet.ui.screens.passcode

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.vultisig.wallet.R
import com.vultisig.wallet.data.passcode.PASSCODE_LENGTH
import com.vultisig.wallet.ui.components.inputs.PasscodeInputField
import com.vultisig.wallet.ui.components.inputs.PasscodeInputFieldState
import com.vultisig.wallet.ui.components.v2.scaffold.V2Scaffold
import com.vultisig.wallet.ui.models.passcode.PasscodeEntryError
import com.vultisig.wallet.ui.models.passcode.PasscodeEntryStep
import com.vultisig.wallet.ui.models.passcode.PasscodeEntryUiModel
import com.vultisig.wallet.ui.models.passcode.PasscodeEntryViewModel
import com.vultisig.wallet.ui.navigation.Route.PasscodeEntryAction
import com.vultisig.wallet.ui.theme.Theme

@Composable
internal fun PasscodeEntryScreen(navController: NavHostController) {
    val viewModel = hiltViewModel<PasscodeEntryViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()

    PasscodeEntryScreen(
        state = state,
        textFieldState = viewModel.textFieldState,
        onBackClick = { navController.popBackStack() },
    )
}

@Composable
private fun PasscodeEntryScreen(
    state: PasscodeEntryUiModel,
    textFieldState: TextFieldState,
    onBackClick: () -> Unit,
) {
    V2Scaffold(title = stringResource(state.action.titleRes()), onBackClick = onBackClick) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            // Same reason as the lock screen: the numeric keypad opens with the screen and covers
            // a third of it, so the block has to centre in what is left rather than in the window.
            modifier = Modifier.fillMaxSize().imePadding().padding(horizontal = 16.dp),
        ) {
            Text(
                text = stringResource(state.step.headlineRes()),
                color = Theme.v2.colors.text.primary,
                style = Theme.brockmann.headings.title2,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )

            Text(
                text = state.step.caption(state.action),
                color = Theme.v2.colors.text.tertiary,
                style = Theme.brockmann.supplementary.footnote,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            )

            PasscodeInputField(
                textFieldState = textFieldState,
                enabled = state.isInputEnabled,
                state =
                    if (state.error != null) PasscodeInputFieldState.Error
                    else PasscodeInputFieldState.Default,
                modifier = Modifier.fillMaxWidth().padding(top = 36.dp),
            )

            state.error?.let { error ->
                Text(
                    text = error.message(),
                    color = Theme.v2.colors.alerts.error,
                    style = Theme.brockmann.supplementary.footnote,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                )
            }
        }
    }
}

private fun PasscodeEntryAction.titleRes(): Int =
    when (this) {
        PasscodeEntryAction.Set -> R.string.passcode_entry_title_set
        PasscodeEntryAction.Change -> R.string.passcode_entry_title_change
        PasscodeEntryAction.Disable -> R.string.passcode_entry_title_disable
    }

private fun PasscodeEntryStep.headlineRes(): Int =
    when (this) {
        PasscodeEntryStep.Current -> R.string.passcode_entry_headline_current
        PasscodeEntryStep.New -> R.string.passcode_entry_headline_new
        PasscodeEntryStep.Confirm -> R.string.passcode_entry_headline_confirm
    }

@Composable
private fun PasscodeEntryStep.caption(action: PasscodeEntryAction): String =
    when (this) {
        PasscodeEntryStep.Current ->
            if (action == PasscodeEntryAction.Disable) {
                stringResource(R.string.passcode_entry_caption_current_disable)
            } else {
                stringResource(R.string.passcode_entry_caption_current_change)
            }
        // The digit count comes from the constant the input field and validation share, so the copy
        // cannot drift from the length actually enforced.
        PasscodeEntryStep.New ->
            stringResource(R.string.passcode_entry_caption_new, PASSCODE_LENGTH)
        PasscodeEntryStep.Confirm ->
            stringResource(R.string.passcode_entry_caption_confirm, PASSCODE_LENGTH)
    }

@Composable
private fun PasscodeEntryError.message(): String =
    when (this) {
        is PasscodeEntryError.Wrong -> wrongPasscodeMessage(remainingAttempts)
        is PasscodeEntryError.LockedOut -> lockedOutMessage(remainingSeconds)
        is PasscodeEntryError.Mismatch -> stringResource(R.string.passcode_entry_mismatch)
        is PasscodeEntryError.OperationFailed -> stringResource(R.string.passcode_operation_failed)
        is PasscodeEntryError.NotDigits -> stringResource(R.string.passcode_not_digits)
    }

@Preview
@Composable
private fun PasscodeEntryScreenPreview() {
    PasscodeEntryScreen(
        state =
            PasscodeEntryUiModel(
                action = PasscodeEntryAction.Set,
                step = PasscodeEntryStep.Confirm,
                error = PasscodeEntryError.Mismatch,
            ),
        textFieldState = TextFieldState(),
        onBackClick = {},
    )
}

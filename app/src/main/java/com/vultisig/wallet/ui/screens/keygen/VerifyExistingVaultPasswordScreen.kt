// TODO: Update password verification screen design to match Figma
package com.vultisig.wallet.ui.screens.keygen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
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
import com.vultisig.wallet.ui.components.inputs.VsTextInputFieldType
import com.vultisig.wallet.ui.components.v2.scaffold.V2Scaffold
import com.vultisig.wallet.ui.models.keygen.VerifyExistingVaultPasswordUiModel
import com.vultisig.wallet.ui.models.keygen.VerifyExistingVaultPasswordViewModel
import com.vultisig.wallet.ui.theme.Theme
import com.vultisig.wallet.ui.utils.asString

@Composable
internal fun VerifyExistingVaultPasswordScreen(
  model: VerifyExistingVaultPasswordViewModel = hiltViewModel()
) {
  val state by model.state.collectAsState()

  VerifyExistingVaultPasswordScreen(
    state = state,
    passwordFieldState = model.passwordFieldState,
    onVerifyClick = model::verify,
    onBackClick = model::back,
    onTogglePasswordVisibilityClick = model::togglePasswordVisibility,
  )
}

@Composable
internal fun VerifyExistingVaultPasswordScreen(
  state: VerifyExistingVaultPasswordUiModel,
  passwordFieldState: TextFieldState,
  onVerifyClick: () -> Unit,
  onBackClick: () -> Unit,
  onTogglePasswordVisibilityClick: () -> Unit,
) {
  V2Scaffold(
    title = null,
    onBackClick = onBackClick,
    content = {
      Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
          text = stringResource(R.string.keysign_password_enter_your_password),
          style = Theme.brockmann.headings.largeTitle,
          color = Theme.v2.colors.text.primary,
        )

        UiSpacer(8.dp)

        val focusRequester = remember { FocusRequester() }
        LaunchedEffect(Unit) { focusRequester.requestFocus() }

        VsTextInputField(
          textFieldState = passwordFieldState,
          hint = stringResource(R.string.keysign_password_title),
          type =
            VsTextInputFieldType.Password(
              isVisible = state.isPasswordVisible,
              onVisibilityClick = onTogglePasswordVisibilityClick,
            ),
          focusRequester = focusRequester,
          imeAction = ImeAction.Go,
          onKeyboardAction = { onVerifyClick() },
          innerState =
            if (state.error != null) VsTextInputFieldInnerState.Error
            else VsTextInputFieldInnerState.Default,
          footNote = state.error?.asString(),
          modifier = Modifier.testTag("VerifyExistingVaultPasswordScreen.passwordField"),
        )
      }
    },
    bottomBar = {
      VsButton(
        label = stringResource(R.string.verify_transaction_screen_title),
        state = if (state.isLoading) VsButtonState.Disabled else VsButtonState.Enabled,
        modifier =
          Modifier.fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 24.dp)
            .testTag("VerifyExistingVaultPasswordScreen.verify"),
        onClick = onVerifyClick,
      )
    },
  )
}

@Composable
@Preview
private fun VerifyExistingVaultPasswordScreenPreview() {
  VerifyExistingVaultPasswordScreen(
    state = VerifyExistingVaultPasswordUiModel(),
    passwordFieldState = rememberTextFieldState(),
    onVerifyClick = {},
    onBackClick = {},
    onTogglePasswordVisibilityClick = {},
  )
}

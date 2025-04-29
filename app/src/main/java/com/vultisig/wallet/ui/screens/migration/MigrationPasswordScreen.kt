package com.vultisig.wallet.ui.screens.migration

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import com.vultisig.wallet.ui.screens.keysign.InputPasswordScreen

@Composable
internal fun MigrationPasswordScreen(
    model: MigrationPasswordViewModel = hiltViewModel(),
) {
    val state by model.state.collectAsState()

    InputPasswordScreen(
        state = state,
        subtitle = "Enter your password to unlock your Server Share and start the upgrade",
        passwordFieldState = model.passwordFieldState,
        onPasswordVisibilityToggle = model::togglePasswordVisibility,
        onContinueClick = model::proceed,
        onBackClick = model::back,
    )
}

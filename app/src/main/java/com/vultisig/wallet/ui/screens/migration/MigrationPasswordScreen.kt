package com.vultisig.wallet.ui.screens.migration

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.screens.keysign.InputPasswordScreen

@Composable
internal fun MigrationPasswordScreen(
    model: MigrationPasswordViewModel = hiltViewModel(),
) {
    val state by model.state.collectAsState()

    InputPasswordScreen(
        state = state,
        subtitle = stringResource(R.string.migration_password_enter_your_password_to_unlock),
        passwordFieldState = model.passwordFieldState,
        onPasswordVisibilityToggle = model::togglePasswordVisibility,
        onContinueClick = model::proceed,
        onBackClick = model::back,
    )
}

package com.vultisig.wallet.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.NamingComponent
import com.vultisig.wallet.ui.models.NamingVaultViewModel
import com.vultisig.wallet.ui.theme.Theme
import com.vultisig.wallet.ui.utils.asString

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun NamingVaultScreen(
    navController: NavHostController,
    model: NamingVaultViewModel = hiltViewModel()
) {
    val state by model.state.collectAsState()

    NamingComponent(
        title = stringResource(id = R.string.naming_vault_screen_setup),
        textFieldState = model.namingTextFieldState,
        navHostController = navController,
        inputTitle = stringResource(id = R.string.naming_vault_screen_vault_name),
        hint = state.placeholder,
        hintColor = Theme.colors.neutral500,
        saveButtonText = stringResource(id = R.string.naming_vault_screen_continue),
        onSave = { model.navigateToKeygen() },
        errorText = model.errorMessageState.collectAsState().value
    )
}
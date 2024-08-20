package com.vultisig.wallet.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.NamingComponent
import com.vultisig.wallet.ui.models.NamingVaultViewModel
import com.vultisig.wallet.ui.theme.Theme

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun NamingVaultScreen(
    navController: NavHostController,
) {
    val viewModel = hiltViewModel<NamingVaultViewModel>()
    val placeholder = stringResource(id = R.string.naming_vault_screen_vault_placeholder)

    NamingComponent(
        title = stringResource(id = R.string.naming_vault_screen_setup),
        textFieldState = viewModel.namingTextFieldState,
        navHostController = navController,
        inputTitle = stringResource(id = R.string.naming_vault_screen_vault_name),
        hint = placeholder,
        hintColor = Theme.colors.neutral500,
        saveButtonText = stringResource(id = R.string.naming_vault_screen_continue),
        onSave = { viewModel.navigateToKeygen(placeholder) },
        onLostFocus = viewModel::validate,
        errorText = viewModel.errorMessageState.collectAsState().value
    )
}
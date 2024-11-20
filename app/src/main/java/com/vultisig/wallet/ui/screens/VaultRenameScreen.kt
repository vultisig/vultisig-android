package com.vultisig.wallet.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.NamingComponent
import com.vultisig.wallet.ui.models.VaultRenameViewModel


@Composable
internal fun VaultRenameScreen(
    navController: NavHostController,
    viewModel: VaultRenameViewModel = hiltViewModel()
) {

    LaunchedEffect(key1 = Unit) {
        viewModel.loadData()
    }

    val uiState by viewModel.uiState.collectAsState()

    NamingComponent(
        title = stringResource(id = R.string.rename_vault_screen_edit_your_vault_name),
        inputTitle = stringResource(id = R.string.rename_vault_screen_vault_name),
        saveButtonText = stringResource(id = R.string.rename_vault_screen_continue),
        textFieldState = viewModel.renameTextFieldState,
        navHostController = navController,
        errorText = uiState.errorMessage,
        onLostFocus = viewModel::validate,
        onSave = viewModel::saveName,
        isLoading = uiState.isLoading,
    )
}
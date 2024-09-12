package com.vultisig.wallet.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.NamingComponent
import com.vultisig.wallet.ui.models.VaultRenameViewModel

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun VaultRenameScreen(
    navController: NavHostController,
    viewModel: VaultRenameViewModel = hiltViewModel()
) {

    LaunchedEffect(key1 = Unit) {
        viewModel.loadData()
    }

    NamingComponent(
        title = stringResource(id = R.string.rename_vault_screen_edit_your_vault_name),
        inputTitle = stringResource(id = R.string.rename_vault_screen_vault_name),
        saveButtonText = stringResource(id = R.string.rename_vault_screen_continue),
        textFieldState = viewModel.renameTextFieldState,
        navHostController = navController,
        errorText = viewModel.errorMessageState.collectAsState().value,
        onLostFocus = viewModel::validate,
        onSave = viewModel::saveName
    )
}
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
import com.vultisig.wallet.ui.models.NamingVaultViewModel

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun NamingVaultScreen(
    navController: NavHostController,
) {
    val viewModel = hiltViewModel<NamingVaultViewModel>()

    LaunchedEffect(Unit) {
        viewModel.collectNamingFieldStateChanges()
    }

    NamingComponent(
        title = stringResource(id = R.string.naming_vault_screen_setup),
        textFieldState = viewModel.namingTextFieldState,
        navHostController = navController,
        inputTitle = stringResource(id = R.string.naming_vault_screen_vault_name),
        saveButtonText = stringResource(id = R.string.naming_vault_screen_continue),
        onSave = viewModel::navigateToKeygen,
        errorText = viewModel.errorMessageState.collectAsState().value
    )
}
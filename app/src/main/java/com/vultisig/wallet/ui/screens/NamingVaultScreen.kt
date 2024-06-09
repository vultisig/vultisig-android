package com.vultisig.wallet.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.NamingComponent
import com.vultisig.wallet.ui.models.NamingVaultViewModel
import com.vultisig.wallet.ui.navigation.Screen

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun NamingVaultScreen(
    navController: NavHostController,
) {
    val viewModel = hiltViewModel<NamingVaultViewModel>()
    val name = viewModel.namingTextFiledState.text.toString()

    NamingComponent(
        title = stringResource(id = R.string.naming_vault_screen_setup),
        textFieldState = viewModel.namingTextFiledState,
        navHostController = navController,
        inputTitle = stringResource(id = R.string.naming_vault_screen_vault_name),
        saveButtonText = stringResource(id = R.string.naming_vault_screen_continue),
        validator = viewModel::validateVaultName
    ) {
        navController.navigate(Screen.KeygenFlow.createRoute(
            name.takeIf { it.isNotEmpty() } ?: Screen.KeygenFlow.DEFAULT_NEW_VAULT,
            viewModel.vaultSetupType))
    }



}
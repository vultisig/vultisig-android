package com.vultisig.wallet.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.NamingComponent
import com.vultisig.wallet.ui.models.NamingVaultViewModel

@Composable
internal fun NamingVaultScreen(
    navController: NavHostController,
) {
    val viewModel = hiltViewModel<NamingVaultViewModel>()
    val uiModel by viewModel.uiModel.collectAsState()


    NamingComponent(
        title = stringResource(id = R.string.naming_vault_screen_name_your_vault),
        onSave = viewModel::onContinueClick,
        name = uiModel.name,
        navHostController = navController,
        onChangeName = viewModel::onNameChanged,
        inputTitle = stringResource(id = R.string.naming_vault_screen_vault_name)
    )
}
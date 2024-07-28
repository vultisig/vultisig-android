package com.vultisig.wallet.presenter.vault_setting.vault_edit

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.vultisig.wallet.R
import com.vultisig.wallet.common.asString
import com.vultisig.wallet.presenter.vault_setting.vault_edit.VaultEditUiEvent.ShowSnackBar
import com.vultisig.wallet.ui.components.NamingComponent

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun VaultRenameScreen(
    navController: NavHostController,
    viewModel: VaultRenameViewModel = hiltViewModel()
) {
    val snackBarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    LaunchedEffect(key1 = Unit) {
        viewModel.loadData()
        viewModel.channelFlow.collect { event ->
            when (event) {
                is ShowSnackBar ->
                    snackBarHostState.showSnackbar(event.message.asString(context))
            }
        }
    }

    NamingComponent(
        title = stringResource(id = R.string.rename_vault_screen_edit_your_vault_name),
        inputTitle = stringResource(id = R.string.rename_vault_screen_vault_name),
        saveButtonText = stringResource(id = R.string.rename_vault_screen_continue),
        textFieldState = viewModel.renameTextFieldState,
        navHostController = navController,
        snackBarHostState = snackBarHostState,
        errorText = viewModel.errorMessageState.collectAsState().value,
        onLostFocus = viewModel::validate,
        onSave = viewModel::saveName
    )
}
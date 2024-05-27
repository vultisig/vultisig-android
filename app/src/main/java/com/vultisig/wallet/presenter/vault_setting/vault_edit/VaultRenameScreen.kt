package com.vultisig.wallet.presenter.vault_setting.vault_edit

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.vultisig.wallet.R
import com.vultisig.wallet.common.asString
import com.vultisig.wallet.presenter.vault_setting.vault_edit.VaultEditEvent.OnNameChange
import com.vultisig.wallet.presenter.vault_setting.vault_edit.VaultEditEvent.OnSave
import com.vultisig.wallet.presenter.vault_setting.vault_edit.VaultEditUiEvent.*
import com.vultisig.wallet.ui.components.NamingComponent

@Composable
internal fun VaultRenameScreen(
    navController: NavHostController,
    viewModel: VaultRenameViewModel = hiltViewModel()
) {
    val uiModel by viewModel.uiModel.collectAsState()
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
        title = stringResource(id = R.string.vault_settings_rename_title),
        inputTitle = stringResource(id = R.string.vault_settings_rename_subtitle),
        onSave = { viewModel.onEvent(OnSave) },
        onChangeName = { newName ->
            viewModel.onEvent(OnNameChange(newName))
        },
        name = uiModel.name,
        navHostController = navController,
        snackBarHostState = snackBarHostState
    )
}

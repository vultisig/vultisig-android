package com.vultisig.wallet.ui.screens.vault_settings

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.vultisig.wallet.R
import com.vultisig.wallet.common.backupVaultToDownloadsDir
import com.vultisig.wallet.ui.components.SettingsItem
import com.vultisig.wallet.ui.components.TopBar
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Screen
import com.vultisig.wallet.ui.screens.vault_settings.VaultSettingsUiEvent.BackupFailed
import com.vultisig.wallet.ui.screens.vault_settings.VaultSettingsUiEvent.BackupFile
import com.vultisig.wallet.ui.screens.vault_settings.VaultSettingsUiEvent.BackupSuccess
import com.vultisig.wallet.ui.screens.vault_settings.components.ConfirmDeleteScreen
import com.vultisig.wallet.ui.theme.Theme

@Composable
internal fun VaultSettingsScreen(
    navController: NavController,
) {
    val viewModel = hiltViewModel<VaultSettingsViewModel>()
    val uiModel by viewModel.uiModel.collectAsState()
    val context = LocalContext.current
    val snackBarHostState = remember { SnackbarHostState() }


    LaunchedEffect(key1 = Unit) {
        viewModel.channelFlow.collect { event ->
            when (event) {
                is BackupSuccess ->
                    snackBarHostState.showSnackbar(
                        context.getString(
                            R.string.vault_settings_success_backup_file,
                            event.backupFileName
                        )
                    )

                BackupFailed ->
                    snackBarHostState.showSnackbar(
                        context.getString(
                            R.string.vault_settings_error_backup_file,
                        )
                    )

                is BackupFile ->
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        val isSuccess = context.backupVaultToDownloadsDir(event.json, event.backupFileName)
                        if (isSuccess)
                            viewModel.successBackup(event.backupFileName)
                        else
                            viewModel.errorBackUp()
                    }
            }
        }
    }

    Scaffold(
        snackbarHost = {
            SnackbarHost(snackBarHostState)
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .consumeWindowInsets(padding)
                .background(Theme.colors.oxfordBlue800)
                .fillMaxSize(),
        ) {
            TopBar(
                navController = navController,
                startIcon = R.drawable.caret_left,
                centerText = stringResource(R.string.vault_settings_title)
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(all = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                SettingsItem(
                    title = stringResource(R.string.vault_settings_details_title),
                    subtitle = stringResource(R.string.vault_settings_details_subtitle),
                    icon = android.R.drawable.ic_menu_info_details,
                ) {
                    uiModel.id.let { vaultName ->
                        navController.navigate(Destination.Details(vaultName).route)
                    }
                }

                SettingsItem(
                    title = stringResource(R.string.vault_settings_backup_title),
                    subtitle = stringResource(R.string.vault_settings_backup_subtitle),
                    icon = R.drawable.download_simple,
                    onClick = viewModel::backupVault)

                SettingsItem(
                    title = stringResource(R.string.vault_settings_rename_title),
                    subtitle = stringResource(R.string.vault_settings_rename_subtitle),
                    icon = R.drawable.pencil
                ) {
                    uiModel.id.let { vaultName ->
                        navController.navigate(Destination.Rename(vaultName).route)
                    }
                }

                SettingsItem(
                    title = stringResource(R.string.vault_settings_reshare_title),
                    subtitle = stringResource(R.string.vault_settings_reshare_subtitle),
                    icon = R.drawable.share
                ) {
                    navController.navigate(
                        Destination.KeygenFlow(
                            uiModel.id,
                        ).route
                    )
                }

                SettingsItem(
                    title = stringResource(R.string.vault_settings_delete_title),
                    subtitle = stringResource(R.string.vault_settings_delete_subtitle),
                    icon = R.drawable.trash_outline,
                    colorTint = Theme.colors.red,
                    onClick = viewModel::showConfirmDeleteDialog
                )
            }
        }
    }

    if (uiModel.showDeleteConfirmScreen) {
        ConfirmDeleteScreen(
            cautions = uiModel.cautionsBeforeDelete,
            checkedCautionIndexes = uiModel.checkedCautionIndexes,
            isDeleteButtonActive = uiModel.isDeleteButtonEnabled,
            onDismissClick = viewModel::dismissConfirmDeleteDialog,
            onItemCheckChangeClick = viewModel::changeCheckCaution,
            onConfirmClick = viewModel::delete
        )
    }
}

@Preview
@Composable
private fun VaultSettingsScreenPreview() {
    VaultSettingsScreen(navController = rememberNavController())
}
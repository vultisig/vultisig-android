package com.vultisig.wallet.ui.screens.vault_settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
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
import com.vultisig.wallet.ui.components.SettingsItem
import com.vultisig.wallet.ui.components.TopBar
import com.vultisig.wallet.ui.components.canAuthenticateBiometric
import com.vultisig.wallet.ui.theme.Theme

@Composable
internal fun VaultSettingsScreen(
    navController: NavController,
) {
    val viewModel = hiltViewModel<VaultSettingsViewModel>()
    val uiModel by viewModel.uiModel.collectAsState()
    val snackBarHostState = remember { SnackbarHostState() }

    VaultSettingsScreen(
        uiModel = uiModel,
        snackBarHostState = snackBarHostState,
        navController = navController,
        onDetailsClick = viewModel::openDetails,
        onRenameClick = viewModel::openRename,
        onBackupClick = viewModel::navigateToBackupPasswordScreen,
        onReshareClick = viewModel::navigateToReshareStartScreen,
        onBiometricsClick = viewModel::navigateToBiometricsScreen,
        onDeleteClick = viewModel::navigateToConfirmDeleteScreen,
    )
}

@Composable
private fun VaultSettingsScreen(
    uiModel: VaultSettingsState,
    snackBarHostState: SnackbarHostState,
    navController: NavController,
    onDetailsClick: () -> Unit = {},
    onRenameClick: () -> Unit = {},
    onBackupClick: () -> Unit = {},
    onReshareClick: () -> Unit = {},
    onBiometricsClick: () -> Unit = {},
    onDeleteClick: () -> Unit = {},
) {
    val context = LocalContext.current
    val canAuthenticateBiometric = remember { context.canAuthenticateBiometric() }

    Scaffold(
        snackbarHost = {
            SnackbarHost(snackBarHostState)
        },
        topBar = {
            TopBar(
                navController = navController,
                startIcon = R.drawable.ic_caret_left,
                centerText = stringResource(R.string.vault_settings_title)
            )
        }
    ) { padding ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .background(Theme.colors.oxfordBlue800)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SettingsItem(
                title = stringResource(R.string.vault_settings_details_title),
                subtitle = stringResource(R.string.vault_settings_details_subtitle),
                icon = android.R.drawable.ic_menu_info_details,
                onClick = onDetailsClick,
            )

            SettingsItem(
                title = stringResource(R.string.vault_settings_backup_title),
                subtitle = stringResource(R.string.vault_settings_backup_subtitle),
                icon = R.drawable.download_simple,
                onClick = onBackupClick
            )

            SettingsItem(
                title = stringResource(R.string.vault_settings_rename_title),
                subtitle = stringResource(R.string.vault_settings_rename_subtitle),
                icon = R.drawable.pencil,
                onClick = onRenameClick,
            )

            SettingsItem(
                title = stringResource(R.string.vault_settings_reshare_title),
                subtitle = stringResource(R.string.vault_settings_reshare_subtitle),
                icon = R.drawable.share,
                onClick = onReshareClick
            )

            if (uiModel.hasFastSign && canAuthenticateBiometric) {
                SettingsItem(
                    title = stringResource(R.string.vault_settings_biometrics_title),
                    subtitle = stringResource(R.string.vault_settings_biometrics_description),
                    icon = R.drawable.ic_biometric,
                    onClick = onBiometricsClick,
                )
            }

            SettingsItem(
                title = stringResource(R.string.vault_settings_delete_title),
                subtitle = stringResource(R.string.vault_settings_delete_subtitle),
                icon = R.drawable.trash_outline,
                colorTint = Theme.colors.red,
                onClick = onDeleteClick
            )
        }
    }
}

@Preview
@Composable
private fun VaultSettingsScreenPreview() {
    VaultSettingsScreen(
        uiModel = VaultSettingsState(),
        snackBarHostState = SnackbarHostState(),
        navController = rememberNavController()
    )
}
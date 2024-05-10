package com.vultisig.wallet.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.vultisig.wallet.R
import com.vultisig.wallet.common.UiEvent
import com.vultisig.wallet.presenter.common.TopBar
import com.vultisig.wallet.ui.components.SettingsItem
import com.vultisig.wallet.ui.navigation.Screen
import com.vultisig.wallet.ui.navigation.Screen.VaultDetail.VaultSettings
import com.vultisig.wallet.ui.theme.appColor

@Composable
internal fun VaultSettingsScreen(
    navController: NavController,
) {
    val viewModel = hiltViewModel<VaultSettingViewModel>()

    LaunchedEffect(key1 = Unit) {
        viewModel.channelFlow.collect{ event->
            when(event){
                is UiEvent.NavigateToScreen -> navController.navigate(event.route)
                UiEvent.NavigateUp -> {
                    navController.popBackStack()
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .background(MaterialTheme.appColor.oxfordBlue800)
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
            ){
                navController.navigate(VaultSettings.Details.createRoute())
            }

            SettingsItem(
                title = stringResource(R.string.vault_settings_backup_title),
                subtitle = stringResource(R.string.vault_settings_backup_subtitle),
                icon = R.drawable.download_simple
            )

            SettingsItem(
                title = stringResource(R.string.vault_settings_rename_title),
                subtitle = stringResource(R.string.vault_settings_rename_subtitle),
                icon = R.drawable.pencil
            ){
                navController.navigate(VaultSettings.Rename.createRoute())
            }

            SettingsItem(
                title = stringResource(R.string.vault_settings_reshare_title),
                subtitle = stringResource(R.string.vault_settings_reshare_subtitle),
                icon = R.drawable.share
            ){
                navController.navigate(Screen.CreateNewVault.route)
            }

            SettingsItem(
                title = stringResource(R.string.vault_settings_delete_title),
                subtitle = stringResource(R.string.vault_settings_delete_subtitle),
                icon = R.drawable.trash_outline,
                colorTint = MaterialTheme.appColor.red,
                onClick = viewModel::deleteVault
            )
        }
    }
}

@Preview
@Composable
private fun VaultSettingsScreenPreview() {
    VaultSettingsScreen(navController = rememberNavController())
}
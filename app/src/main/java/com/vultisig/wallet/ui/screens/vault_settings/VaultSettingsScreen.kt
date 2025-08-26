package com.vultisig.wallet.ui.screens.vault_settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.topbar.VsTopAppBar
import com.vultisig.wallet.ui.screens.settings.SettingItem
import com.vultisig.wallet.ui.screens.settings.SettingsBox
import com.vultisig.wallet.ui.theme.Theme

@Composable
internal fun VaultSettingsScreen() {
    val viewModel = hiltViewModel<VaultSettingsViewModel>()
    val uiModel by viewModel.uiModel.collectAsState()

    BackHandler(onBack = viewModel::onBackClick)

    VaultSettingsScreen(
        uiModel = uiModel,
        onSettingsClick = {
            viewModel.onSettingsItemClick(it)
        },
        onBackClick = viewModel::onBackClick
    )
}

@Composable
private fun VaultSettingsScreen(
    uiModel: VaultSettingsState,
    onSettingsClick: (VaultSettingsItem) -> Unit,
    onBackClick: () -> Unit = {}
) {

    val settingGroups = uiModel.settingGroups

    Scaffold(
        topBar = {
            VsTopAppBar(
                title = if (uiModel.isAdvanceSetting)
                    stringResource(R.string.eth_gas_settings_title)
                else
                    stringResource(R.string.vault_settings_title),
                iconLeft = com.vultisig.wallet.R.drawable.ic_caret_left,
                onIconLeftClick = onBackClick
            )
        }
    ) {
        Column(
            Modifier
                .padding(it)
                .padding(
                    horizontal = 16.dp,
                    vertical = 12.dp
                )
                .verticalScroll(rememberScrollState())
        ) {
            settingGroups.forEach { group ->
                if (group.isVisible) {
                    SettingsBox(title = group.title) {
                        val visibleItems = group.items.filter { it.enabled }
                        visibleItems.forEachIndexed { index, item ->
                            SettingItem(
                                item = item.value,
                                onClick = { onSettingsClick(item) },
                                isLastItem = index == visibleItems.lastIndex,
                                tint = if (item is VaultSettingsItem.Delete)
                                    Theme.colors.alerts.error else null
                            )
                        }
                    }
                }
                    UiSpacer(14.dp)
                }
            }
        }
    }
}
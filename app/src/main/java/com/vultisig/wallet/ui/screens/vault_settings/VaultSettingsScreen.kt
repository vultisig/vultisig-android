package com.vultisig.wallet.ui.screens.vault_settings

import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.UiIcon
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.bottomsheet.VsModalBottomSheet
import com.vultisig.wallet.ui.components.clickOnce
import com.vultisig.wallet.ui.components.v2.containers.ContainerBorderType
import com.vultisig.wallet.ui.components.v2.containers.ContainerType
import com.vultisig.wallet.ui.components.v2.containers.V2Container
import com.vultisig.wallet.ui.components.v2.containers.V2Scaffold
import com.vultisig.wallet.ui.screens.send.FadingHorizontalDivider
import com.vultisig.wallet.ui.screens.settings.SettingItem
import com.vultisig.wallet.ui.screens.settings.SettingsBox
import com.vultisig.wallet.ui.theme.Theme
import com.vultisig.wallet.ui.utils.asString

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
        onBackClick = viewModel::onBackClick,
        onLocalBackupClick = {
            viewModel.onLocalBackupClick()
        },
        onServerBackupClick = {
            viewModel.onServerBackupClick()
        },
        onDismissBackupVaultBottomSheet = {
            viewModel.onDismissBackupVaultBottomSheet()
        }
    )
}

@Composable
private fun VaultSettingsScreen(
    uiModel: VaultSettingsState,
    onSettingsClick: (VaultSettingsItem) -> Unit,
    onBackClick: () -> Unit = {},
    onLocalBackupClick: () -> Unit = {},
    onServerBackupClick: () -> Unit = {},
    onDismissBackupVaultBottomSheet: () -> Unit = {},
) {

    val settingGroups = uiModel.settingGroups

    V2Scaffold(
        title = if (uiModel.isAdvanceSetting)
            stringResource(R.string.vault_settings_advanced_title)
        else
            stringResource(R.string.vault_settings_title),
       onBackClick =  onBackClick,
    ) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
        ) {
            settingGroups.filter(VaultSettingsGroupUiModel::isVisible).forEach { group ->
                SettingsBox(title = group.title?.asString()) {
                    val enabledSettings = group.items.filter(VaultSettingsItem::enabled)
                    enabledSettings.forEachIndexed { index, item ->
                        SettingItem(
                            item = item.value,
                            onClick = { onSettingsClick(item) },
                            isLastItem = index == enabledSettings.lastIndex,
                            tint = if (item is VaultSettingsItem.Delete)
                                Theme.colors.alerts.error else null
                        )
                    }
                }
                UiSpacer(14.dp)
            }


            if (uiModel.isBackupVaultBottomSheetVisible) {
                BackupVaultBottomSheet(
                    onDismissRequest = onDismissBackupVaultBottomSheet,
                    onLocalBackupClick = onLocalBackupClick,
                    onServerBackupClick = onServerBackupClick
                )
            }
        }
    }


}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BackupVaultBottomSheet(
    onDismissRequest: () -> Unit = {},
    onLocalBackupClick: () -> Unit = {},
    onServerBackupClick: () -> Unit = {},
) {
    VsModalBottomSheet(
        onDismissRequest = onDismissRequest,
    ) {
        BackupVaultBottomSheetContent(
            onLocalBackupClick = onLocalBackupClick,
            onServerBackupClick = onServerBackupClick
        )
    }
}

@Preview
@Composable
private fun BackupVaultBottomSheetContent(
    onLocalBackupClick: () -> Unit = {},
    onServerBackupClick: () -> Unit = {},
) {
    Column(
        Modifier
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.backup_choose_method_title),
            style = Theme.brockmann.headings.subtitle,
            color = Theme.colors.text.primary,
        )

        FadingHorizontalDivider(
            modifier = Modifier
                .padding(
                    vertical = 24.dp
                )
        )

        BackupOption(
            title = stringResource(R.string.backup_device_title),
            description = stringResource(R.string.backup_device_desc),
            icon = R.drawable.device_backup,
            onClick = onLocalBackupClick
        )

        UiSpacer(
            size = 14.dp
        )
        BackupOption(
            title = stringResource(R.string.backup_server_title),
            description = stringResource(R.string.backup_server_desc),
            icon = R.drawable.server_backup,
            onClick = onServerBackupClick
        )
        UiSpacer(14.dp)
    }
}

@Composable
private fun BackupOption(
    title: String,
    description: String,
    @DrawableRes icon: Int,
    onClick: () -> Unit,

    ) {
    V2Container(
        modifier = Modifier.clickOnce(onClick = onClick),
        type = ContainerType.PRIMARY,
        borderType = ContainerBorderType.Bordered(color = Theme.colors.borders.normal),
    ) {
        Row(
            modifier = Modifier
                .padding(
                    all = 16.dp
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            UiIcon(
                drawableResId = icon,
                size = 20.dp,
                tint = Theme.colors.primary.accent4,
            )

            UiSpacer(
                size = 12.dp,
            )

            Column(
                modifier = Modifier
                    .weight(1f)
            ) {
                Text(
                    text = title,
                    style = Theme.brockmann.headings.subtitle,
                    color = Theme.colors.text.primary,
                )

                UiSpacer(
                    size = 4.dp
                )

                Text(
                    text = description,
                    style = Theme.brockmann.supplementary.caption,
                    color = Theme.colors.text.light,
                )
            }

            UiIcon(
                drawableResId = R.drawable.ic_caret_right,
                size = 20.dp,
                tint = Theme.colors.text.light,
            )
        }
    }
}

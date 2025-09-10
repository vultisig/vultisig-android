package com.vultisig.wallet.ui.screens.backup

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.UiIcon
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.v2.containers.ContainerBorderType
import com.vultisig.wallet.ui.components.v2.containers.ContainerType
import com.vultisig.wallet.ui.components.v2.containers.V2Container
import com.vultisig.wallet.ui.components.v2.scaffold.V2Scaffold
import com.vultisig.wallet.ui.screens.send.FadingHorizontalDivider
import com.vultisig.wallet.ui.theme.Theme


@Composable
internal fun VaultsToBackupScreen() {
    val viewModel = hiltViewModel<VaultsToBackupViewModel>()
    val uiState by viewModel.uiState.collectAsState()
    VaultsToBackupScreen(
        onBackClick = viewModel::back,
        backupVaultUiModel = uiState,
    )
}

@Composable
internal fun VaultsToBackupScreen(
    onBackClick: () -> Unit,
    backupVaultUiModel: BackupVaultUiModel,
) {
    V2Scaffold(
        onBackClick = onBackClick,
        modifier = Modifier
            .background(color = Theme.colors.backgrounds.primary),
    ) {
        Column {
            Text(
                text = stringResource(R.string.backup_select_vaults_title),
                style = Theme.brockmann.headings.title1,
                color = Theme.colors.text.primary,
            )

            UiSpacer(
                size = 12.dp,
            )

            Text(
                text = stringResource(R.string.backup_select_vaults_subtitle),
                style = Theme.brockmann.body.s.medium,
                color = Theme.colors.text.extraLight,
            )

            UiSpacer(
                size = 36.dp,
            )

            BackupVaultContainer(
                title = stringResource(R.string.backup_this_vault_only),
                vaults = listOf(
                    backupVaultUiModel.currentVault,
                )
            )
            UiSpacer(
                size = 16.dp,
            )

            BackupVaultContainer(
                title = stringResource(R.string.backup_all_vaults),
                vaults = backupVaultUiModel.vaultsToBackup,
                remainedCount = backupVaultUiModel.remainedCount,
            )
        }
    }
}

@Composable
private fun BackupVaultContainer(
    title: String,
    vaults: List<VaultToBackupUiModel>,
    remainedCount: Int? = null,
) {
    V2Container(
        type = ContainerType.PRIMARY,
        borderType = ContainerBorderType.Bordered(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = 12.dp,
                    vertical = 14.dp,
                ),
        ) {
            Text(
                text = title,
                color = Theme.colors.text.extraLight,
                style = Theme.brockmann.body.s.medium,
            )
            UiSpacer(
                size = 12.dp,
            )

            V2Container(
                type = ContainerType.SECONDARY,
                borderType = ContainerBorderType.Borderless
            ) {
                vaults.forEachIndexed { index, vault ->
                    VaultToBackup(
                        model = vault,
                        isLastItem = index == vaults.lastIndex,
                    )
                }

            }

            UiSpacer(
                size = 12.dp,
            )

            RemainedCountText(remainedCount)

        }
    }
}

@Composable
private fun RemainedCountText(remainedCount: Int?) {
    remainedCount?.let {
        Text(
            text = stringResource(
                R.string.more,
                remainedCount
            ),
            color = Theme.colors.text.light,
            style = Theme.brockmann.supplementary.footnote,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}


@Composable
internal fun VaultToBackup(
    model: VaultToBackupUiModel,
    isLastItem: Boolean
) {
    Column {
        Row(
            modifier = Modifier
                .padding(
                    all = 20.dp,
                ),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = model.name,
                style = Theme.brockmann.body.s.medium,
                color = Theme.colors.text.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )

            UiSpacer(24.dp)


            VaultMetaInfo(model)
        }
        if (!isLastItem) {
            FadingHorizontalDivider()
        }
    }
}

@Composable
private fun VaultMetaInfo(model: VaultToBackupUiModel) {
    V2Container(
        type = ContainerType.SECONDARY,
        borderType = ContainerBorderType.Bordered(),
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = 12.dp,
                vertical = 8.dp,
            ),
            verticalAlignment = Alignment.CenterVertically,
        ) {

            UiIcon(
                drawableResId = if (model.isFast) {
                    R.drawable.thunder
                } else {
                    R.drawable.ic_shield
                },
                contentDescription = "vault type logo",
                size = 16.dp,
                tint = if (model.isFast) Theme.colors.alerts.warning else Theme.colors.alerts.success,
            )
            UiSpacer(
                size = 4.dp,
            )

            Text(
                text = stringResource(
                    R.string.vault_details_screen_vault_part_desc,
                    model.part,
                    model.size
                ),
                style = Theme.brockmann.body.s.medium,
                color = Theme.colors.text.primary,
            )

        }
    }
}


@Composable
@Preview
internal fun PreviewVaultsToBackupScreen() {
    VaultsToBackupScreen(
        onBackClick = {},
        backupVaultUiModel = BackupVaultUiModel(
            currentVault = VaultToBackupUiModel(
                name = "Vault Name",
                part = 2,
                size = 3,
                isFast = false,
            ),
            vaultsToBackup = listOf(
                VaultToBackupUiModel(
                    name = "Main Vault",
                    part = 2,
                    size = 3,
                    isFast = false,
                ),
                VaultToBackupUiModel(
                    name = "A longer vault name A longer vault name",
                    part = 1,
                    size = 2,
                    isFast = true,
                ),
                VaultToBackupUiModel(
                    name = "Cold Vault",
                    part = 2,
                    size = 3,
                    isFast = false,
                ),
                VaultToBackupUiModel(
                    name = "Vault Name",
                    part = 2,
                    size = 3,
                    isFast = false,
                ),
            ),
            remainedCount = 10
        )
    )
}
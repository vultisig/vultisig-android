package com.vultisig.wallet.ui.screens.backup

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.UiIcon
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.clickOnce
import com.vultisig.wallet.ui.components.v2.containers.ContainerBorderType
import com.vultisig.wallet.ui.components.v2.containers.ContainerType
import com.vultisig.wallet.ui.components.v2.containers.V2Container
import com.vultisig.wallet.ui.components.v2.icons.VaultIcon
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
        onCurrentVaultBackupClick = viewModel::backupCurrentVault,
        onAllVaultsBackupClick = viewModel::backupAllVaults,
    )
}

@Composable
internal fun VaultsToBackupScreen(
    backupVaultUiModel: BackupVaultUiModel,
    onBackClick: () -> Unit,
    onCurrentVaultBackupClick: () -> Unit,
    onAllVaultsBackupClick: () -> Unit,
) {
    V2Scaffold(
        onBackClick = onBackClick,
        modifier = Modifier
            .background(color = Theme.v2.colors.backgrounds.primary),
    ) {
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize()
        ) {
            val maxHeight = maxHeight
            var headerHeight by remember { mutableStateOf(0.dp) }
            var firstContainerHeight by remember { mutableStateOf(0.dp) }
            var spacerHeight by remember { mutableStateOf(0.dp) }
            val density = LocalDensity.current

            Column {
                SelectVaultTypeHeader(
                    modifier = Modifier.onGloballyPositioned { coordinates ->
                        headerHeight = with(density) { coordinates.size.height.toDp() }
                    }
                )

                BackupVaultContainer(
                    modifier = Modifier.onGloballyPositioned { coordinates ->
                        firstContainerHeight = with(density) { coordinates.size.height.toDp() }
                    },
                    title = stringResource(R.string.backup_this_vault_only),
                    vaults = listOf(
                        backupVaultUiModel.currentVault,
                    ),
                    onClick = onCurrentVaultBackupClick,
                    availableHeight = null
                )


                Box(
                    modifier = Modifier.onGloballyPositioned { coordinates ->
                        spacerHeight = with(density) { coordinates.size.height.toDp() }
                    }
                ) {
                    UiSpacer(
                        size = 16.dp,
                    )
                }

                BackupVaultContainer(
                    title = stringResource(R.string.backup_all_vaults),
                    vaults = backupVaultUiModel.vaultsToBackup,
                    onClick = onAllVaultsBackupClick,
                    availableHeight = maxHeight - headerHeight - firstContainerHeight - spacerHeight
                )
            }
        }
    }
}

@Composable
private fun SelectVaultTypeHeader(
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
    ) {
        Text(
            text = stringResource(R.string.backup_select_vaults_title),
            style = Theme.brockmann.headings.title1,
            color = Theme.v2.colors.text.primary,
        )

        UiSpacer(
            size = 12.dp,
        )

        Text(
            text = stringResource(R.string.backup_select_vaults_subtitle),
            style = Theme.brockmann.body.s.medium,
            color = Theme.v2.colors.text.tertiary,
        )

        UiSpacer(
            size = 36.dp,
        )
    }
}

@Composable
private fun BackupVaultContainer(
    modifier: Modifier = Modifier,
    title: String,
    vaults: List<VaultToBackupUiModel>,
    onClick: () -> Unit,
    availableHeight: Dp?,
) {
    V2Container(
        type = ContainerType.PRIMARY,
        borderType = ContainerBorderType.Bordered(),
        modifier = modifier.clickOnce(onClick = onClick)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = 12.dp,
                    vertical = 14.dp,
                ),
        ) {
            var headerHeight by remember { mutableStateOf(0.dp) }
            val topSpacerHeight = 12.dp
            val bottomSpacerHeight = 12.dp
            val containerPadding = 28.dp

            val density = LocalDensity.current

            Column(
                modifier = Modifier.onGloballyPositioned { coordinates ->
                    if (headerHeight == 0.dp) {
                        headerHeight = with(density) { coordinates.size.height.toDp() }
                    }
                }
            ) {
                Row {
                    Text(
                        text = title,
                        color = Theme.v2.colors.text.tertiary,
                        style = Theme.brockmann.body.s.medium,
                    )
                    UiSpacer(weight = 1f)
                    UiIcon(
                        drawableResId = R.drawable.ic_small_caret_right,
                        size = 16.dp,
                        tint = Theme.v2.colors.text.tertiary,
                    )
                }
                UiSpacer(size = topSpacerHeight)
            }

            var visibleItemsCount by remember { mutableIntStateOf(0) }
            val remainingCount = (vaults.size - visibleItemsCount).coerceAtLeast(0)

            if (availableHeight != null && headerHeight > 0.dp) {
                val vaultListMaxHeight =
                    (availableHeight - headerHeight - topSpacerHeight - bottomSpacerHeight - containerPadding).coerceAtLeast(
                        0.dp
                    )

                V2Container(
                    type = ContainerType.SECONDARY,
                    borderType = ContainerBorderType.Borderless,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = vaultListMaxHeight)
                ) {
                    BoxWithConstraints {
                        val containerMaxHeight = this.maxHeight
                        val itemHeights = remember { mutableStateMapOf<Int, Dp>() }

                        Column(Modifier.verticalScroll(rememberScrollState())) {
                            vaults.forEachIndexed { index, vault ->
                                Box(
                                    modifier = Modifier.onSizeChanged {
                                        val itemHeight = with(density) { it.height.toDp() }
                                        if (itemHeights[index] == null) {
                                            itemHeights[index] = itemHeight

                                            var tempHeight = 0.dp
                                            var count = 0
                                            for (i in 0..vaults.lastIndex) {
                                                val h = itemHeights[i]
                                                    ?: itemHeight
                                                if (tempHeight + h <= containerMaxHeight) {
                                                    tempHeight += h
                                                    count++
                                                } else {
                                                    break
                                                }
                                            }
                                            visibleItemsCount = count
                                        }
                                    }
                                ) {
                                    if (index < visibleItemsCount || visibleItemsCount == 0) {
                                        VaultToBackup(
                                            model = vault,
                                            isLastItem = index == vaults.lastIndex,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            } else if (availableHeight == null) {
                V2Container(
                    type = ContainerType.SECONDARY,
                    borderType = ContainerBorderType.Borderless,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column {
                        vaults.forEachIndexed { index, vault ->
                            VaultToBackup(
                                model = vault,
                                isLastItem = index == vaults.lastIndex
                            )
                        }
                    }
                    visibleItemsCount = vaults.size
                }
            }


            UiSpacer(size = bottomSpacerHeight)

            if (availableHeight != null && remainingCount != 0) {
                RemainedCountText(remainingCount)
            }
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
            color = Theme.v2.colors.text.secondary,
            style = Theme.brockmann.supplementary.footnote,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}


@Composable
internal fun VaultToBackup(
    model: VaultToBackupUiModel,
    isLastItem: Boolean,
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
                color = Theme.v2.colors.text.primary,
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
            VaultIcon(isFastVault = model.isFast)
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
                color = Theme.v2.colors.text.primary,
            )

        }
    }
}


@Composable
@Preview
internal fun PreviewVaultsToBackupScreen() {
    VaultsToBackupScreen(
        onBackClick = {},
        onCurrentVaultBackupClick = {},
        onAllVaultsBackupClick = {},
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
        )
    )
}
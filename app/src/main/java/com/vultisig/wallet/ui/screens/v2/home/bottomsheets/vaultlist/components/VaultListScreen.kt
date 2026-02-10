package com.vultisig.wallet.ui.screens.v2.home.bottomsheets.vaultlist.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vultisig.wallet.R
import com.vultisig.wallet.data.models.FiatValue
import com.vultisig.wallet.data.models.Folder
import com.vultisig.wallet.data.models.Vault
import com.vultisig.wallet.data.models.getVaultPart
import com.vultisig.wallet.data.models.isFastVault
import com.vultisig.wallet.data.usecases.VaultAndBalance
import com.vultisig.wallet.ui.components.UiIcon
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.VaultCeil
import com.vultisig.wallet.ui.components.VaultCeilUiModel
import com.vultisig.wallet.ui.components.buttons.VsButton
import com.vultisig.wallet.ui.components.buttons.VsButtonVariant
import com.vultisig.wallet.ui.components.reorderable.VerticalDoubleReorderList
import com.vultisig.wallet.ui.components.reorderable.VerticalReorderList
import com.vultisig.wallet.ui.components.v2.bottomsheets.navhost.VsBottomSheetNavController
import com.vultisig.wallet.ui.components.v2.buttons.DesignType
import com.vultisig.wallet.ui.components.v2.buttons.VsCircleButton
import com.vultisig.wallet.ui.components.v2.buttons.VsCircleButtonSize
import com.vultisig.wallet.ui.components.v2.buttons.VsCircleButtonType
import com.vultisig.wallet.ui.components.v2.texts.LoadableValue
import com.vultisig.wallet.ui.models.home.FolderAndVaultsCount
import com.vultisig.wallet.ui.models.home.VaultListUiModel
import com.vultisig.wallet.ui.models.home.VaultListViewModel
import com.vultisig.wallet.ui.navigation.Route
import com.vultisig.wallet.ui.theme.Theme
import java.math.BigDecimal


@Composable
internal fun VaultListScreen(
    openType: Route.VaultList.OpenType,
    navController: VsBottomSheetNavController,
) {
    val viewModel = hiltViewModel<VaultListViewModel>()
    val state by viewModel.state.collectAsState()
    LaunchedEffect(openType) {
        viewModel.init(openType)
    }

    when(openType){
        is Route.VaultList.OpenType.DeepLink -> VaultListScreen(
            state = state,
            onSelectVault = viewModel::selectVault,
        )

        is Route.VaultList.OpenType.Home -> {
            VaultListScreen(
                state = state,
                onSelectVault = viewModel::selectVault,
                onSelectFolder = { folderId ->
                    navController.navigate(
                        Route.FolderList(
                            folderId = folderId,
                            vaultId = openType.vaultId
                        )
                    )
                },
                onCreateNewVault = viewModel::addVault,
                onCreateNewFolder = {
                    navController.navigate(
                        Route.CreateFolder(
                            folderId = null
                        )
                    )
                },
                onMoveVaults = viewModel::onMoveVaults,
                onMoveFolders = viewModel::onMoveFolders,
                onToggleRearrangeMode = viewModel::toggleRearrangeMode
            )
        }
    }
}


@Composable
private fun VaultListScreen(
    state: VaultListUiModel,
    onSelectVault: (vaultId: String) -> Unit = {},
    onSelectFolder: (folderId: String) -> Unit = {},
    onCreateNewVault: () -> Unit = {},
    onCreateNewFolder: () -> Unit = {},
    onMoveVaults: (from: Int, to: Int) -> Unit = { _, _ -> },
    onMoveFolders: (from: Int, to: Int) -> Unit = { _, _ -> },
    onToggleRearrangeMode: () -> Unit = {},
) {
    val isInEditMode = state.isRearrangeMode

    Column(
        modifier = Modifier
            .padding(
                horizontal = 16.dp
            )
    ) {
        UiSpacer(
            size = 24.dp
        )

        AnimatedContent(isInEditMode) { isRearrangeMode ->
            if (isRearrangeMode) {
                VaultsRearrangeHeader(
                    onCommitClick = onToggleRearrangeMode
                )
            } else {
                VaultsInfoHeader(
                    vaultCounts = state.totalVaultsCount,
                    totalBalance = state.totalBalance,
                    onToggleRearrangeMode = onToggleRearrangeMode,
                    onCreateNewVault = onCreateNewVault
                )
            }
        }


        VerticalDoubleReorderList(
            modifier = Modifier
                .weight(
                    weight = 1f,
                    fill = false
                ),
            midContents = listOf {
                UiSpacer(size = 16.dp)
                Text(
                    text = stringResource(id = R.string.vault_list_vaults_list),
                    color = Theme.v2.colors.text.tertiary,
                    style = Theme.brockmann.supplementary.caption,
                    modifier = Modifier
                        .padding(horizontal = 12.dp)
                )
                UiSpacer(size = 8.dp)
            },
            onMoveT = onMoveFolders,
            onMoveR = onMoveVaults,
            dataT = state.folders,
            dataR = state.vaults,
            isReorderEnabled = isInEditMode,
            keyR = { it.vault.id },
            keyT = { it.folder.id },
            contentPadding = PaddingValues(
                vertical = 16.dp,
            ),
            contentR = { vaultAndBalance ->
                VaultCeil(
                    model = VaultCeilUiModel(
                        id = vaultAndBalance.vault.id,
                        name = vaultAndBalance.vault.name,
                        isFolder = false,
                        isFastVault = vaultAndBalance.vault.isFastVault(),
                        vaultPart = vaultAndBalance.vault.getVaultPart(),
                        signersSize = vaultAndBalance.vault.signers.size,
                        balance = vaultAndBalance.balance
                    ),
                    isInEditMode = isInEditMode,
                    onSelect = onSelectVault,
                    isSelected = vaultAndBalance.vault.id == state.currentVaultId,
                    activeVaultName = null,
                    vaultCounts = null
                )
            },
            contentT = { folderAndVaultsCount ->
                VaultCeil(
                    model = VaultCeilUiModel(
                        id = folderAndVaultsCount.folder.id.toString(),
                        name = folderAndVaultsCount.folder.name,
                        isFolder = true,
                    ),
                    isInEditMode = isInEditMode,
                    onSelect = onSelectFolder,
                    isSelected = folderAndVaultsCount.folder.id.toString() == state.currentFolderId,
                    activeVaultName = state.currentVaultName.takeIf { folderAndVaultsCount.folder.id.toString() == state.currentFolderId },
                    vaultCounts = folderAndVaultsCount.vaultsCount
                )
            },
        )

        if (isInEditMode && state.vaults.isNotEmpty()) {
            VsButton(
                modifier = Modifier
                    .fillMaxWidth(),
                onClick = onCreateNewFolder,
                variant = VsButtonVariant.Tertiary
            ) {
                UiIcon(
                    drawableResId = R.drawable.ic_folder,
                    size = 20.dp,
                    tint = Theme.v2.colors.neutrals.n100
                )
                Text(
                    text = stringResource(R.string.vault_list_add_folder),
                    style = Theme.brockmann.button.semibold.semibold,
                    color = Theme.v2.colors.neutrals.n100
                )
            }
        }

        UiSpacer(
            size = 24.dp
        )
    }
}

@Composable
private fun VaultListScreen(
    state: VaultListUiModel,
    onSelectVault: (vaultId: String) -> Unit,
) {

    Column(
        modifier = Modifier
            .padding(
                horizontal = 16.dp
            )
    ) {
        UiSpacer(
            size = 24.dp
        )

        VaultsInfoHeader(
            vaultCounts = state.totalVaultsCount,
            totalBalance = state.totalBalance,
            onToggleRearrangeMode = null,
            onCreateNewVault = null
        )


        VerticalReorderList(
            modifier = Modifier
                .weight(
                    weight = 1f,
                    fill = false
                ),
            onMove = { _ , _ ->},
            data = state.vaults,
            isReorderEnabled = false,
            key = { it.vault.id },
            contentPadding = PaddingValues(
                vertical = 16.dp,
            ),
            content = { vaultAndBalance ->
                VaultCeil(
                    model = VaultCeilUiModel(
                        id = vaultAndBalance.vault.id,
                        name = vaultAndBalance.vault.name,
                        isFolder = false,
                        isFastVault = vaultAndBalance.vault.isFastVault(),
                        vaultPart = vaultAndBalance.vault.getVaultPart(),
                        signersSize = vaultAndBalance.vault.signers.size,
                        balance = vaultAndBalance.balance
                    ),
                    isInEditMode = false,
                    onSelect = onSelectVault,
                    isSelected = vaultAndBalance.vault.id == state.currentVaultId,
                    activeVaultName = null,
                    vaultCounts = null
                )
            },
        )
        UiSpacer(
            size = 24.dp
        )
    }
}

@Composable
private fun VaultsInfoHeader(
    vaultCounts: Int,
    totalBalance: String?,
    onToggleRearrangeMode: (() -> Unit)?,
    onCreateNewVault: (() -> Unit)?,
) {
    Row {
        VaultInfo(
            vaultName = stringResource(R.string.vault_list_vaults_list),
            vaultCounts = vaultCounts,
            totalBalance = totalBalance
        )

        UiSpacer(
            weight = 1f
        )

        onToggleRearrangeMode?.let {
            VsCircleButton(
                designType = DesignType.Shined,
                size = VsCircleButtonSize.Small,
                type = VsCircleButtonType.Secondary,
                icon = R.drawable.pen_v2,
                onClick = it
            )
        }

        onCreateNewVault?.let {

            UiSpacer(
                size = 8.dp
            )

            VsCircleButton(
                designType = DesignType.Shined,
                size = VsCircleButtonSize.Small,
                type = VsCircleButtonType.Primary,
                icon = R.drawable.plus,
                onClick = it
            )
        }
    }
}

@Composable
internal fun VaultInfo(
    vaultName: String,
    vaultCounts: Int, totalBalance: String?,
) {
    Column {
        Text(
            text = vaultName,
            style = Theme.brockmann.headings.title3,
            color = Theme.v2.colors.neutrals.n100,
        )

        UiSpacer(
            size = 4.dp
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
        ) {

            Text(
                text = "$vaultCounts Vault${if (vaultCounts != 1) "s" else ""}",
                style = Theme.brockmann.supplementary.footnote,
                color = Theme.v2.colors.text.tertiary,
            )

            UiSpacer(
                size = 6.dp
            )

            Box(
                modifier = Modifier
                    .size(2.dp)
                    .background(
                        color = Theme.v2.colors.text.primary,
                    )
            )

            UiSpacer(
                size = 6.dp
            )
            LoadableValue(
                value = totalBalance,
                style = Theme.brockmann.supplementary.footnote,
                color = Theme.v2.colors.text.tertiary,
                isVisible = true
            )
        }
    }
}

@Composable
private fun VaultsRearrangeHeader(
    onCommitClick: () -> Unit,
) {
    BottomSheetHeader(
        title = stringResource(R.string.vault_list_edit_vaults_header),
    ) {
        VsCircleButton(
            designType = DesignType.Shined,
            size = VsCircleButtonSize.Small,
            type = VsCircleButtonType.Primary,
            drawableResId = R.drawable.big_tick,
            onClick = onCommitClick,
        )
    }
}


@Composable
internal fun BottomSheetHeader(
    title: String,
    rightAction: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
    ) {
        Text(
            text = title,
            style = Theme.brockmann.headings.title3,
            color = Theme.v2.colors.neutrals.n100,
            modifier = Modifier
                .align(Alignment.Center)
        )

        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd),
            content = rightAction
        )
    }
}

@Preview
@Composable
private fun VaultListScreenPreview() {
    VaultListScreen(
        state = VaultListUiModel(
            vaults = listOf(
                VaultAndBalance(
                    vault = Vault(
                        id = "1",
                        name = "Vault 1",
                    ),
                    balance = "$13.42",
                    balanceFiatValue = FiatValue(BigDecimal.valueOf(12.42), "USD")
                ),
                VaultAndBalance(
                    vault = Vault(
                        id = "2",
                        name = "Vault 2",
                    ),
                    balance = "$10.15",
                    balanceFiatValue = null,
                )
            ),
            folders = listOf(
                FolderAndVaultsCount(
                    folder = Folder(id = 1, name = "Folder 1"),
                    vaultsCount = 2,
                ),
                FolderAndVaultsCount(
                    folder = Folder(id = 2, name = "Folder 2"),
                    vaultsCount = 1,
                ),
                FolderAndVaultsCount(
                    folder = Folder(id = 3, name = "Folder 3"),
                    vaultsCount = 4,
                ),
            ),
            isRearrangeMode = false,
        )
    )
}

@Preview
@Composable
private fun VaultListScreenPreview2() {
    VaultListScreen(
        state = VaultListUiModel(
            vaults = listOf(
                VaultAndBalance(
                    vault = Vault(
                        id = "1",
                        name = "Vault 1",
                    ),
                    balance = "$13.42",
                    balanceFiatValue = FiatValue(BigDecimal.valueOf(12.42), "USD")
                ),
                VaultAndBalance(
                    vault = Vault(
                        id = "2",
                        name = "Vault 2",
                    ),
                    balance = "$10.15",
                    balanceFiatValue = null,
                )
            ),
        ),
        onSelectVault = {

        }
    )
}
package com.vultisig.wallet.ui.screens.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment.Companion.CenterHorizontally
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vultisig.wallet.R
import com.vultisig.wallet.data.models.Folder
import com.vultisig.wallet.data.models.Vault
import com.vultisig.wallet.data.models.getVaultPart
import com.vultisig.wallet.data.models.isFastVault
import com.vultisig.wallet.ui.components.MultiColorButton
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.VaultCeil
import com.vultisig.wallet.ui.components.VaultCeilUiModel
import com.vultisig.wallet.ui.components.reorderable.VerticalDoubleReorderList
import com.vultisig.wallet.ui.models.home.VaultListViewModel
import com.vultisig.wallet.ui.theme.Theme

@Composable
internal fun VaultListScreen(
    onSelectVault: (vaultId: String) -> Unit = {},
    onSelectFolder: (folderId: String) -> Unit = {},
    onCreateNewVault: () -> Unit = {},
    onCreateNewFolder: () -> Unit = {},
    viewModel: VaultListViewModel = hiltViewModel(),
    isRearrangeMode: Boolean,
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.initVaultListData()
    }

    VaultListScreen(
        vaults = state.vaults,
        folders = state.folders,
        isRearrangeMode = isRearrangeMode,
        onMoveVaults = viewModel::onMoveVaults,
        onMoveFolders = viewModel::onMoveFolders,
        onSelectVault = onSelectVault,
        onSelectFolder = onSelectFolder,
        onCreateNewVault = onCreateNewVault,
        onCreateNewFolder = onCreateNewFolder,
    )
}

@Composable
private fun VaultListScreen(
    isRearrangeMode: Boolean,
    vaults: List<Vault>,
    folders: List<Folder>,
    onSelectVault: (vaultId: String) -> Unit = {},
    onSelectFolder: (folderId: String) -> Unit = {},
    onCreateNewVault: () -> Unit = {},
    onCreateNewFolder: () -> Unit = {},
    onMoveVaults: (from: Int, to: Int) -> Unit = { _, _ -> },
    onMoveFolders: (from: Int, to: Int) -> Unit = { _, _ -> },
) {
    Scaffold(
        content = { contentPadding ->
            VerticalDoubleReorderList(
                beforeContents = listOf {
                    Text(
                        text = stringResource(id = R.string.folders_list_title),
                        color = Theme.colors.neutral0,
                        style = Theme.montserrat.body2,
                    )
                    UiSpacer(size = 16.dp)
                },
                midContents = listOf {
                    Text(
                        text = stringResource(id = R.string.home_screen_title),
                        color = Theme.colors.neutral0,
                        style = Theme.montserrat.body2,
                    )
                    UiSpacer(size = 16.dp)
                },
                onMoveT = onMoveFolders,
                onMoveR = onMoveVaults,
                dataT = folders,
                dataR = vaults,
                isReorderEnabled = isRearrangeMode,
                keyR = { it.id },
                keyT = { it.id },
                contentPadding = PaddingValues(
                    vertical = 16.dp,
                    horizontal = 16.dp,
                ),
                modifier = Modifier.padding(contentPadding),
                contentR = { vault ->
                VaultCeil(
                    model = VaultCeilUiModel(
                        id = vault.id,
                        name = vault.name,
                        isFolder = false,
                        isFastVault = vault.isFastVault(),
                        vaultPart = vault.getVaultPart(),
                        signersSize = vault.signers.size,
                    ),
                    isInEditMode = isRearrangeMode,
                    onSelect = onSelectVault,
                )
            },
                contentT = { folder ->
                    VaultCeil(
                        model = VaultCeilUiModel(
                            id = folder.id.toString(),
                            name = folder.name,
                            isFolder = true,
                        ),
                        isInEditMode = isRearrangeMode,
                        onSelect = onSelectFolder,
                    )
                },
            )
        },
        bottomBar = {
            Column(
                horizontalAlignment = CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = 16.dp,
                        vertical = 12.dp,
                    ),
            ) {
                if (!isRearrangeMode) {
                    MultiColorButton(
                        text = stringResource(R.string.home_screen_add_new_vault),
                        backgroundColor = Theme.colors.turquoise800,
                        textColor = Theme.colors.oxfordBlue800,
                        iconColor = Theme.colors.turquoise800,
                        textStyle = Theme.montserrat.subtitle1,
                        modifier = Modifier.fillMaxWidth(),
                        onClick = onCreateNewVault,
                    )
                } else if (vaults.isNotEmpty()) {
                    MultiColorButton(
                        text = stringResource(R.string.create_folder),
                        backgroundColor = Theme.colors.oxfordBlue800,
                        textColor = Theme.colors.turquoise800,
                        iconColor = Theme.colors.oxfordBlue800,
                        borderSize = 1.dp,
                        textStyle = Theme.montserrat.subtitle1,
                        modifier = Modifier.fillMaxWidth(),
                        onClick = onCreateNewFolder,
                    )

                    UiSpacer(size = 12.dp)
                }
            }
        },
    )
}

@Preview(showBackground = true, name = "VaultListScreen")
@Composable
private fun VaultListScreenPreview() {
    VaultListScreen(
        isRearrangeMode = false,
        vaults = listOf(
            Vault(
                id = "1",
                name = "Vault 1",
            ), Vault(
                id = "2",
                name = "Vault 2",
            ), Vault(
                id = "3",
                name = "Vault 3",
            )
        ),
        folders = listOf(
            Folder(id = 1, name = "Folder 1"),
            Folder(id = 2, name = "Folder 2"),
            Folder(id = 3, name = "Folder 3"),
        ),
    )
}
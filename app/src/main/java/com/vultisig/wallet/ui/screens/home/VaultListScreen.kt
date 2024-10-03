package com.vultisig.wallet.ui.screens.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment.Companion.CenterHorizontally
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vultisig.wallet.R
import com.vultisig.wallet.data.db.models.FolderEntity
import com.vultisig.wallet.data.models.Vault
import com.vultisig.wallet.data.models.VaultListEntity
import com.vultisig.wallet.ui.components.MultiColorButton
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.VaultCeil
import com.vultisig.wallet.ui.components.reorderable.VerticalReorderList
import com.vultisig.wallet.ui.models.home.VaultListViewModel
import com.vultisig.wallet.ui.theme.Theme

@Composable
internal fun VaultListScreen(
    onSelect: (vaultListEntity: VaultListEntity) -> Unit = {},
    onCreateNewVault: () -> Unit = {},
    onImportVaultClick: () -> Unit = {},
    viewModel: VaultListViewModel = hiltViewModel(),
    isRearrangeMode: Boolean,
) {
    val vaultListItems by viewModel.listItems.collectAsState()

    VaultListScreen(
        vaultListItems = vaultListItems,
        isRearrangeMode = isRearrangeMode,
        onMove = viewModel::onMove,
        onSelect = onSelect,
        onCreateNewVault = onCreateNewVault,
        onImportVaultClick = onImportVaultClick,
        onCreateNewFolder = viewModel::onCreateNewFolder,
    )
}

@Composable
private fun VaultListScreen(
    isRearrangeMode: Boolean,
    vaultListItems: List<VaultListEntity>,
    onSelect: (vaultListEntity: VaultListEntity) -> Unit = {},
    onCreateNewVault: () -> Unit = {},
    onCreateNewFolder: () -> Unit = {},
    onImportVaultClick: () -> Unit = {},
    onMove: (from: Int, to: Int) -> Unit = { _, _ -> },
) {
    Scaffold(
        content = { contentPadding ->
            VerticalReorderList(
                onMove = onMove,
                data = vaultListItems,
                isReorderEnabled = isRearrangeMode,
                key = { it.id },
                contentPadding = PaddingValues(
                    vertical = 16.dp,
                    horizontal = 16.dp,
                ),
                modifier = Modifier
                    .padding(contentPadding),
            ) { vault ->
                VaultCeil(
                    vaultListEntity = vault,
                    isInEditMode = isRearrangeMode,
                    onSelect = onSelect,
                )
            }
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
                if (isRearrangeMode) {
                    MultiColorButton(
                        text = stringResource(R.string.create_folder),
                        backgroundColor = Theme.colors.oxfordBlue800,
                        textColor = Theme.colors.turquoise800,
                        iconColor = Theme.colors.oxfordBlue800,
                        borderSize = 1.dp,
                        textStyle = Theme.montserrat.subtitle1,
                        modifier = Modifier
                            .fillMaxWidth(),
                        onClick = onCreateNewFolder,
                    )

                    UiSpacer(size = 12.dp)
                } else {
                    MultiColorButton(
                        text = stringResource(R.string.home_screen_add_new_vault),
                        backgroundColor = Theme.colors.turquoise800,
                        textColor = Theme.colors.oxfordBlue800,
                        iconColor = Theme.colors.turquoise800,
                        textStyle = Theme.montserrat.subtitle1,
                        modifier = Modifier
                            .fillMaxWidth(),
                        onClick = onCreateNewVault,
                    )

                    UiSpacer(size = 12.dp)

                    MultiColorButton(
                        text = stringResource(R.string.home_screen_import_vault),
                        backgroundColor = Theme.colors.oxfordBlue800,
                        textColor = Theme.colors.turquoise800,
                        iconColor = Theme.colors.oxfordBlue800,
                        borderSize = 1.dp,
                        textStyle = Theme.montserrat.subtitle1,
                        modifier = Modifier
                            .fillMaxWidth(),
                        onClick = onImportVaultClick,
                    )
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
        vaultListItems = listOf(
            VaultListEntity.VaultListItem(
                Vault(
                    id = "1",
                    name = "Vault 1",
                )
            ),
            VaultListEntity.VaultListItem(
                Vault(
                    id = "2",
                    name = "Vault 2",
                )
            ),
            VaultListEntity.VaultListItem(
                Vault(
                    id = "3",
                    name = "Vault 3",
                )
            ),
            VaultListEntity.FolderListItem(
                folder = FolderEntity(
                    id = 1,
                    name = "Folder 1",
                ),
            ),
        ),
    )
}
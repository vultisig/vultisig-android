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
import com.vultisig.wallet.data.models.Vault
import com.vultisig.wallet.ui.components.MultiColorButton
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.VaultCeil
import com.vultisig.wallet.ui.components.reorderable.VerticalReorderList
import com.vultisig.wallet.ui.models.home.VaultListViewModel
import com.vultisig.wallet.ui.theme.Theme

@Composable
internal fun VaultListScreen(
    onSelectVault: (vaultId: String) -> Unit = {},
    onCreateNewVault: () -> Unit = {},
    onImportVaultClick: () -> Unit = {},
    viewModel: VaultListViewModel = hiltViewModel(),
    isRearrangeMode: Boolean,
) {
    val vaults by viewModel.vaults.collectAsState()

    VaultListScreen(
        vaults = vaults,
        isRearrangeMode = isRearrangeMode,
        onMove = viewModel::onMove,
        onSelectVault = onSelectVault,
        onCreateNewVault = onCreateNewVault,
        onImportVaultClick = onImportVaultClick,
        onCreateNewFolder = viewModel::onCreateNewFolder,
    )
}

@Composable
private fun VaultListScreen(
    isRearrangeMode: Boolean,
    vaults: List<Vault>,
    onSelectVault: (vaultId: String) -> Unit = {},
    onCreateNewVault: () -> Unit = {},
    onCreateNewFolder: () -> Unit = {},
    onImportVaultClick: () -> Unit = {},
    onMove: (from: Int, to: Int) -> Unit = { _, _ -> },
) {
    Scaffold(
        content = { contentPadding ->
            VerticalReorderList(
                onMove = onMove,
                data = vaults,
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
                    vault = vault,
                    isInEditMode = isRearrangeMode,
                    onSelectVault = onSelectVault,
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
        vaults = listOf(
            Vault(
                id = "1",
                name = "Vault 1",
            ),
            Vault(
                id = "2",
                name = "Vault 2",
            ),
            Vault(
                id = "3",
                name = "Vault 3",
            ),
        ),
    )
}
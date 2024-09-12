package com.vultisig.wallet.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.vultisig.wallet.R
import com.vultisig.wallet.data.models.Vault
import com.vultisig.wallet.ui.components.MultiColorButton
import com.vultisig.wallet.ui.components.VaultCeil
import com.vultisig.wallet.ui.components.reorderable.VerticalReorderList
import com.vultisig.wallet.ui.models.home.VaultListViewModel
import com.vultisig.wallet.ui.theme.Theme

@Composable
internal fun VaultListScreen(
    navController: NavHostController,
    onSelectVault: (vaultId: String) -> Unit = {},
    onCreateNewVault: () -> Unit = {},
    viewModel: VaultListViewModel = hiltViewModel(),
    isRearrangeMode: Boolean,
) {
    val vaults by viewModel.vaults.collectAsState()

    VaultListScreen(
        navController = navController,
        vaults = vaults,
        isRearrangeMode = isRearrangeMode,
        onMove = viewModel::onMove,
        onSelectVault = onSelectVault,
        onCreateNewVault = onCreateNewVault,
    )
}

@Composable
private fun VaultListScreen(
    navController: NavHostController,
    isRearrangeMode: Boolean,
    vaults: List<Vault>,
    onSelectVault: (vaultId: String) -> Unit = {},
    onCreateNewVault: () -> Unit = {},
    onMove: (from: Int, to: Int) -> Unit = { _, _ -> },
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Theme.colors.oxfordBlue800)
            .clickable(enabled = false, onClick = {})
    ) {
        VerticalReorderList(
            onMove = onMove,
            data = vaults,
            isReorderEnabled = isRearrangeMode,
            key = { it.id },
            contentPadding = PaddingValues(
                top = 16.dp,
                start = 12.dp,
                end = 12.dp,
                bottom = 64.dp,
            )
        ) { vault ->
            VaultCeil(
                vault = vault,
                isInEditMode = isRearrangeMode,
                onSelectVault = onSelectVault,
            )
        }

        MultiColorButton(
            text = stringResource(R.string.home_screen_add_new_vault),
            backgroundColor = Theme.colors.turquoise800,
            textColor = Theme.colors.oxfordBlue800,
            iconColor = Theme.colors.turquoise800,
            textStyle = Theme.montserrat.subtitle1,
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    vertical = 16.dp,
                    horizontal = 12.dp,
                )
                .align(Alignment.BottomCenter)
        ) {
            onCreateNewVault()
        }
    }
}

@Preview(showBackground = true, name = "VaultListScreen")
@Composable
private fun VaultListScreenPreview() {
    val navController = rememberNavController()
    VaultListScreen(
        navController = navController,
        isRearrangeMode = false,
        vaults = listOf(
            Vault(
                id = "",
                name = "Vault 1",
            ),
            Vault(
                id = "",
                name = "Vault 2",
            ),
            Vault(
                id = "",
                name = "Vault 3",
            ),
        ),
    )
}
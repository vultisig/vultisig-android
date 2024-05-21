package com.vultisig.wallet.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.asFlow
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.MultiColorButton
import com.vultisig.wallet.ui.components.VaultCeil
import com.vultisig.wallet.ui.models.home.VaultListViewModel
import com.vultisig.wallet.ui.navigation.Screen
import com.vultisig.wallet.ui.theme.Theme
import com.vultisig.wallet.ui.theme.dimens

@Composable
internal fun VaultListScreen(
    navController: NavHostController,
    onSelectVault: (vaultId: String) -> Unit = {},
    viewModel: VaultListViewModel = hiltViewModel(),
) {
    val vaults = viewModel.vaults.asFlow().collectAsState(initial = emptyList()).value

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Theme.colors.oxfordBlue800)
    ) {
        LazyColumn {
            items(vaults) { vault ->
                VaultCeil(
                    vault = vault,
                    onSelectVault = onSelectVault,
                )
            }
        }

        MultiColorButton(
            text = stringResource(R.string.home_screen_add_new_vault),
            minHeight = MaterialTheme.dimens.minHeightButton,
            backgroundColor = Theme.colors.turquoise800,
            textColor = Theme.colors.oxfordBlue800,
            iconColor = Theme.colors.turquoise800,
            textStyle = Theme.montserrat.subtitle1,
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = MaterialTheme.dimens.marginMedium,
                    end = MaterialTheme.dimens.marginMedium,
                    bottom = MaterialTheme.dimens.marginMedium,
                )
                .align(Alignment.BottomCenter)
        ) {
            navController.navigate(route = Screen.CreateNewVault.route)
        }
    }
}

@Preview(showBackground = true, name = "VaultListScreen")
@Composable
fun VaultListScreenPreview() {
    val navController = rememberNavController()
    VaultListScreen(navController = navController)
}
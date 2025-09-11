package com.vultisig.wallet.ui.screens.referral

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.vultisig.wallet.ui.components.topbar.VsTopAppBar
import com.vultisig.wallet.ui.models.referral.ReferralVaultListUiState
import com.vultisig.wallet.ui.models.referral.ReferralVaultListViewModel
import com.vultisig.wallet.ui.models.referral.VaultItem
import com.vultisig.wallet.ui.theme.Theme

@Composable
internal fun ReferralOnboardingScreen(
    navController: NavController,
    model: ReferralVaultListViewModel = hiltViewModel(),
) {
    val state by model.state.collectAsState()

    ReferralVaultListScreen(
        state = state,
        onBackPress = navController::popBackStack,
        onVaultClicked = {},
    )
}

@Composable
internal fun ReferralVaultListScreen(
    state: ReferralVaultListUiState,
    onBackPress: () -> Unit,
    onVaultClicked: (String) -> Unit,
) {
    Scaffold(
        containerColor = Theme.colors.backgrounds.primary,
        topBar = {
            VsTopAppBar(
                title = "Referral",
                onBackClick = onBackPress,
            )
        },
        content = { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Theme.colors.backgrounds.primary)
                    .imePadding()
                    .navigationBarsPadding()
                    .padding(paddingValues)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
            ) {
                Text(
                    style = Theme.brockmann.body.m.medium,
                    color = Theme.colors.text.extraLight,
                    text = "Vaults"
                )

                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Theme.colors.backgrounds.secondary)
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(state.vaults) { vault ->
                        VaultRow(vault)
                    }
                }
            }
        },
    )
}

@Composable
internal fun VaultRow(vault: VaultItem) {

}

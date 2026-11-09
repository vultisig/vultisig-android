package com.vultisig.wallet.ui.screens.referral

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.v2.icons.VaultIcon
import com.vultisig.wallet.ui.components.v2.scaffold.V2Scaffold
import com.vultisig.wallet.ui.models.referral.ReferralVaultListUiState
import com.vultisig.wallet.ui.models.referral.ReferralVaultListViewModel
import com.vultisig.wallet.ui.models.referral.ReferralVaultListViewModel.Companion.VAULT_ID_SELECTED
import com.vultisig.wallet.ui.models.referral.VaultItem
import com.vultisig.wallet.ui.theme.Theme
import com.vultisig.wallet.ui.utils.asString
import com.vultisig.wallet.ui.utils.asUiText

@Composable
internal fun ReferralVaultListScreen(
    navController: NavController,
    model: ReferralVaultListViewModel = hiltViewModel(),
) {
    val state by model.state.collectAsState()

    ReferralVaultListContentScreen(
        state = state,
        onBackPress = navController::popBackStack,
        onVaultClicked = { vaultId ->
            navController.previousBackStackEntry?.savedStateHandle?.set(VAULT_ID_SELECTED, vaultId)
            model.onVaultClick(vaultId)
        },
    )
}

@Composable
internal fun ReferralVaultListContentScreen(
    state: ReferralVaultListUiState,
    onBackPress: () -> Unit,
    onVaultClicked: (String) -> Unit,
) {

    V2Scaffold(
        title = stringResource(R.string.referral_top_bar_list),
        onBackClick = onBackPress,
        content = {
            Column(
                modifier =
                    Modifier.fillMaxSize()
                        .background(Theme.v2.colors.backgrounds.primary)
                        .imePadding()
                        .navigationBarsPadding()
            ) {
                Text(
                    style = Theme.brockmann.supplementary.caption,
                    color = Theme.v2.colors.text.tertiary,
                    text = stringResource(R.string.referral_top_bar_list_vaults),
                    textAlign = TextAlign.Start,
                )

                UiSpacer(10.dp)

                LazyColumn(
                    modifier =
                        Modifier.clip(Theme.v2.radius.xl)
                            .background(Theme.v2.colors.backgrounds.secondary)
                ) {
                    itemsIndexed(state.vaults, key = { _, vault -> vault.id }) { _, vault ->
                        VaultRow(vault, onVaultClicked)
                    }
                }
            }
        },
    )
}

@Composable
internal fun VaultRow(vault: VaultItem, onVaultClicked: (String) -> Unit) {
    Row(
        modifier =
            Modifier.fillMaxWidth()
                .then(
                    if (vault.isSelected) {
                        Modifier.background(Theme.v2.colors.backgrounds.tertiary_2)
                    } else {
                        Modifier
                    }
                )
                .clickable { onVaultClicked(vault.id) }
                .padding(horizontal = 20.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            painter = painterResource(R.drawable.referral_vault_avatar),
            contentDescription = null,
            modifier = Modifier.size(28.dp),
        )

        UiSpacer(12.dp)

        Text(
            text = vault.name,
            color = Theme.v2.colors.text.primary,
            maxLines = 1,
            style = Theme.brockmann.body.s.medium,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )

        UiSpacer(8.dp)

        VaultSigningPill(vault)

        UiSpacer(8.dp)

        Icon(
            modifier = Modifier.size(if (vault.isSelected) 16.dp else 14.dp),
            painter =
                painterResource(
                    id = if (vault.isSelected) R.drawable.ic_check else R.drawable.ic_caret_right
                ),
            contentDescription = null,
            tint =
                if (vault.isSelected) Theme.v2.colors.alerts.success
                else Theme.v2.colors.text.primary,
        )
    }
}

@Composable
private fun VaultSigningPill(vault: VaultItem) {
    Row(
        modifier =
            Modifier.background(
                    Theme.v2.colors.backgrounds.secondary,
                    shape = Theme.v2.radius.pill,
                )
                .border(
                    border = BorderStroke(width = 1.dp, color = Theme.v2.colors.border.light),
                    shape = Theme.v2.radius.pill,
                )
                .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        VaultIcon(
            isFastVault = vault.isFastVault,
            isSecureIconFilled = true,
            contentDescription = null,
        )

        UiSpacer(4.dp)

        Text(
            text = vault.signingInfo.asString(),
            style = Theme.brockmann.supplementary.caption,
            color = Theme.v2.colors.text.tertiary,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ReferralVaultListScreenPreview() {
    val state =
        ReferralVaultListUiState(
            vaults =
                listOf(
                    VaultItem(
                        id = "1",
                        name = "My Main Vault",
                        isSelected = true,
                        signingInfo = R.string.vault_part_n_of_t.asUiText(1, 3),
                    ),
                    VaultItem(
                        id = "2",
                        name = "Trading Vault",
                        isSelected = false,
                        signingInfo = R.string.vault_part_n_of_t.asUiText(2, 3),
                    ),
                    VaultItem(
                        id = "3",
                        name = "Savings Vault",
                        isSelected = false,
                        signingInfo = R.string.vault_part_n_of_t.asUiText(1, 2),
                    ),
                    VaultItem(
                        id = "4",
                        name = "DeFi Vault",
                        isSelected = false,
                        signingInfo = R.string.vault_part_n_of_t.asUiText(3, 5),
                    ),
                ),
            error = null,
        )

    ReferralVaultListContentScreen(state = state, onBackPress = {}, onVaultClicked = {})
}

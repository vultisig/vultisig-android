package com.vultisig.wallet.ui.screens.referral

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.UiGradientDivider
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.topbar.VsTopAppBar
import com.vultisig.wallet.ui.models.referral.ReferralVaultListUiState
import com.vultisig.wallet.ui.models.referral.ReferralVaultListViewModel
import com.vultisig.wallet.ui.models.referral.VaultItem
import com.vultisig.wallet.ui.theme.Theme

@Composable
internal fun ReferralVaultListScreen(
    navController: NavController,
    model: ReferralVaultListViewModel = hiltViewModel(),
) {
    val state by model.state.collectAsState()

    ReferralVaultListContentScreen(
        state = state,
        onBackPress = navController::popBackStack,
        onVaultClicked = model::onVaultClick,
    )
}

@Composable
internal fun ReferralVaultListContentScreen(
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
                    .padding(16.dp),
            ) {
                Text(
                    style = Theme.brockmann.body.m.medium,
                    color = Theme.colors.text.extraLight,
                    text = "Vaults",
                    textAlign = TextAlign.Start,
                )

                UiSpacer(16.dp)

                LazyColumn(
                    modifier = Modifier
                        .background(Theme.colors.backgrounds.secondary, shape = RoundedCornerShape(12.dp)),
                ) {
                    items(state.vaults.size) { index ->
                        val vault = state.vaults[index]

                        VaultRow(vault, onVaultClicked)
                        
                        if (index < state.vaults.size - 1) {
                            UiGradientDivider(
                                initialColor = Theme.colors.backgrounds.secondary,
                                endColor = Theme.colors.backgrounds.secondary,
                            )
                        }
                    }
                }
            }
        },
    )
}

@Composable
internal fun VaultRow(vault: VaultItem, onVaultClicked: (String) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onVaultClicked(vault.id) }
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = vault.name,
            color = Theme.colors.text.primary,
            maxLines = 1,
            style = Theme.brockmann.body.m.medium,
            overflow = TextOverflow.Ellipsis,
        )

        UiSpacer(1f)

        Row(
            modifier = Modifier
                .background(Theme.colors.backgrounds.secondary, shape = RoundedCornerShape(20.dp))
                .border(
                    border = BorderStroke(
                        width = 1.dp,
                        color = Theme.colors.borders.light
                    ),
                    shape = RoundedCornerShape(20.dp)
                )
                .padding(horizontal = 10.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (vault.isFastVault) {
                Icon(
                    painter = painterResource(id = R.drawable.biomatrics_fast),
                    contentDescription = null,
                    tint = Theme.colors.miamiMarmalade,
                    modifier = Modifier.size(14.dp)
                )
            } else {
                Icon(
                    painter = painterResource(id = R.drawable.ic_shield),
                    contentDescription = null,
                    modifier = Modifier.size(14.dp)
                )
            }

            UiSpacer(6.dp)

            Text(
                text = vault.signingInfo,
                style = Theme.brockmann.supplementary.caption,
                color = Theme.colors.text.extraLight,
            )
        }

        UiSpacer(6.dp)

        if (vault.isSelected) {
            Icon(
                modifier = Modifier.size(20.dp),
                painter = painterResource(id = R.drawable.ic_check),
                contentDescription = null,
                tint = Theme.colors.alerts.success,
            )
        } else {
            Icon(
                modifier = Modifier.size(20.dp),
                painter = painterResource(id = R.drawable.ic_caret_right),
                contentDescription = null,
                tint = Theme.colors.text.primary,
            )
        }
    }
}
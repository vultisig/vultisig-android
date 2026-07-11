package com.vultisig.wallet.ui.screens.v2.defi.solana

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.buttons.VsButton
import com.vultisig.wallet.ui.components.buttons.VsButtonState
import com.vultisig.wallet.ui.components.topbar.VsTopAppBar
import com.vultisig.wallet.ui.models.solanastaking.SolanaMoveStakeUiState
import com.vultisig.wallet.ui.models.solanastaking.SolanaMoveStakeViewModel
import com.vultisig.wallet.ui.theme.Theme

@Composable
internal fun SolanaMoveStakeScreen(viewModel: SolanaMoveStakeViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    SolanaMoveStakeContent(
        state = state,
        onBack = viewModel::back,
        onContinue = viewModel::onContinue,
    )
}

@Composable
internal fun SolanaMoveStakeContent(
    state: SolanaMoveStakeUiState,
    onBack: () -> Unit = {},
    onContinue: () -> Unit = {},
) {
    Column(modifier = Modifier.fillMaxSize().background(Theme.v2.colors.backgrounds.primary)) {
        VsTopAppBar(title = stringResource(R.string.solana_move_title), onBackClick = onBack)

        Column(
            modifier = Modifier.weight(1f).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(
                modifier =
                    Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Theme.v2.colors.backgrounds.secondary)
                        .border(
                            width = 1.dp,
                            color = Theme.v2.colors.border.light,
                            shape = RoundedCornerShape(12.dp),
                        )
                        .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.solana_staking_stake_account),
                        style = Theme.brockmann.body.m.medium,
                        color = Theme.v2.colors.text.primary,
                    )
                    UiSpacer(weight = 1f)
                    Text(
                        text = shortAddress(state.stakePubkey),
                        style = Theme.brockmann.body.m.medium,
                        color = Theme.v2.colors.text.tertiary,
                    )
                }
            }

            Column(
                modifier =
                    Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Theme.v2.colors.backgrounds.secondary)
                        .padding(16.dp)
            ) {
                Text(
                    text = stringResource(R.string.solana_move_notice),
                    style = Theme.brockmann.body.s.medium,
                    color = Theme.v2.colors.text.tertiary,
                )
            }
        }

        state.error?.let {
            Text(
                text = it,
                style = Theme.brockmann.supplementary.caption,
                color = Theme.v2.colors.alerts.error,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            UiSpacer(size = 8.dp)
        }

        VsButton(
            label = stringResource(R.string.cosmos_staking_continue),
            state = if (state.isSubmitting) VsButtonState.Disabled else VsButtonState.Enabled,
            isLoading = state.isSubmitting,
            onClick = onContinue,
            modifier = Modifier.fillMaxWidth().padding(16.dp),
        )
    }
}

private fun shortAddress(address: String): String =
    if (address.length > 12) "${address.take(6)}…${address.takeLast(4)}" else address

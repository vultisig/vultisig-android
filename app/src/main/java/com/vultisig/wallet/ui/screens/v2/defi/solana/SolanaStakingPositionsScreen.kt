package com.vultisig.wallet.ui.screens.v2.defi.solana

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.vultisig.wallet.R
import com.vultisig.wallet.data.models.VaultId
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.models.solanastaking.SolanaStakePositionRow
import com.vultisig.wallet.ui.models.solanastaking.SolanaStakingPositionsUiState
import com.vultisig.wallet.ui.models.solanastaking.SolanaStakingPositionsViewModel
import com.vultisig.wallet.ui.theme.Theme
import com.vultisig.wallet.ui.utils.asString

private val HIDE_BALANCE_CHARS = "• ".repeat(6).trim()

/** Entry point for the Solana native-staking positions screen. */
@Composable
internal fun SolanaStakingPositionsScreen(
    vaultId: VaultId,
    viewModel: SolanaStakingPositionsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    var isRefreshing by remember { mutableStateOf(false) }

    LifecycleResumeEffect(vaultId) {
        viewModel.setData(vaultId)
        onPauseOrDispose {}
    }

    SolanaStakingPositionsContent(
        state = state,
        isRefreshing = isRefreshing,
        onRefresh = {
            isRefreshing = true
            viewModel.refresh()
        },
    )

    if (state !is SolanaStakingPositionsUiState.Loading) {
        isRefreshing = false
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SolanaStakingPositionsContent(
    state: SolanaStakingPositionsUiState,
    isRefreshing: Boolean = false,
    onRefresh: () -> Unit = {},
) {
    PullToRefreshBox(isRefreshing = isRefreshing, onRefresh = onRefresh) {
        Column(modifier = Modifier.fillMaxSize().background(Theme.v2.colors.backgrounds.primary)) {
            SolanaStakingHeader(
                totalFiat =
                    (state as? SolanaStakingPositionsUiState.Success)?.totalStakedFiatDisplay ?: "",
                isBalanceVisible =
                    (state as? SolanaStakingPositionsUiState.Success)?.isBalanceVisible ?: true,
            )

            when (state) {
                is SolanaStakingPositionsUiState.Loading ->
                    CenteredMessage(stringResource(R.string.solana_staking_loading))
                is SolanaStakingPositionsUiState.Error ->
                    CenteredMessage(state.error.asString(), isError = true)
                is SolanaStakingPositionsUiState.Success ->
                    if (state.positions.isEmpty()) {
                        CenteredMessage(stringResource(R.string.solana_staking_no_positions))
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            items(state.positions, key = { it.stakePubkey }) { row ->
                                SolanaStakePositionCard(
                                    row = row,
                                    isBalanceVisible = state.isBalanceVisible,
                                )
                            }
                        }
                    }
            }
        }
    }
}

@Composable
private fun SolanaStakingHeader(totalFiat: String, isBalanceVisible: Boolean) {
    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        Text(
            text = stringResource(R.string.solana_staking_total_staked),
            style = Theme.brockmann.supplementary.footnote,
            color = Theme.v2.colors.text.extraLight,
        )
        UiSpacer(size = 4.dp)
        Text(
            text = if (isBalanceVisible) totalFiat else HIDE_BALANCE_CHARS,
            style = Theme.brockmann.headings.title1,
            color = Theme.v2.colors.text.primary,
        )
    }
}

@Composable
private fun SolanaStakePositionCard(row: SolanaStakePositionRow, isBalanceVisible: Boolean) {
    Row(
        modifier =
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Theme.v2.colors.backgrounds.secondary)
                .border(
                    width = 1.dp,
                    color = Theme.v2.colors.border.light,
                    shape = RoundedCornerShape(16.dp),
                )
                .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MonogramAvatar(name = row.validatorName)
        UiSpacer(size = 12.dp)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = row.validatorName,
                style = Theme.brockmann.body.m.medium,
                color = Theme.v2.colors.text.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            UiSpacer(size = 2.dp)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = row.stateLabel.asString(),
                    style = Theme.brockmann.supplementary.caption,
                    color = Theme.v2.colors.text.extraLight,
                )
                row.apyDisplay?.let {
                    Text(
                        text = "  •  ${stringResource(R.string.solana_staking_apy_label, it)}",
                        style = Theme.brockmann.supplementary.caption,
                        color = Theme.v2.colors.alerts.success,
                    )
                }
            }
        }
        UiSpacer(size = 12.dp)
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = if (isBalanceVisible) row.stakedDisplay else HIDE_BALANCE_CHARS,
                style = Theme.brockmann.body.m.medium,
                color = Theme.v2.colors.text.primary,
            )
            UiSpacer(size = 2.dp)
            Text(
                text = if (isBalanceVisible) row.stakedFiatDisplay else HIDE_BALANCE_CHARS,
                style = Theme.brockmann.supplementary.caption,
                color = Theme.v2.colors.text.extraLight,
            )
        }
    }
}

@Composable
private fun MonogramAvatar(name: String) {
    val letter = name.firstOrNull { it.isLetterOrDigit() }?.uppercaseChar()?.toString() ?: "?"
    Box(
        modifier =
            Modifier.size(36.dp).clip(CircleShape).background(Theme.v2.colors.backgrounds.tertiary),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = letter,
            style = Theme.brockmann.body.s.medium,
            color = Theme.v2.colors.text.primary,
        )
    }
}

@Composable
private fun CenteredMessage(message: String, isError: Boolean = false) {
    Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Text(
            text = message,
            textAlign = TextAlign.Center,
            style = Theme.brockmann.body.m.medium,
            color = if (isError) Theme.v2.colors.alerts.error else Theme.v2.colors.text.extraLight,
        )
    }
}

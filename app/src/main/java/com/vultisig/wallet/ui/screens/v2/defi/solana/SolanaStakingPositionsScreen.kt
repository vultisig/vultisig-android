package com.vultisig.wallet.ui.screens.v2.defi.solana

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.Alignment.Companion.CenterHorizontally
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.vultisig.wallet.R
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.VaultId
import com.vultisig.wallet.ui.components.UiHorizontalDivider
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.v2.tab.VsTab
import com.vultisig.wallet.ui.components.v2.tab.VsTabGroup
import com.vultisig.wallet.ui.models.solanastaking.SolanaStakePositionRow
import com.vultisig.wallet.ui.models.solanastaking.SolanaStakingPositionsUiState
import com.vultisig.wallet.ui.models.solanastaking.SolanaStakingPositionsViewModel
import com.vultisig.wallet.ui.screens.v2.defi.ActionButton
import com.vultisig.wallet.ui.screens.v2.defi.ApyInfoItem
import com.vultisig.wallet.ui.screens.v2.defi.BalanceBanner
import com.vultisig.wallet.ui.screens.v2.defi.HeaderDeFiWidget
import com.vultisig.wallet.ui.screens.v2.defi.InfoItem
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
        onStake = viewModel::onStake,
        onDeactivate = viewModel::onDeactivate,
        onWithdraw = viewModel::onWithdraw,
        onMove = viewModel::onMove,
    )

    if (!state.isLoading) {
        isRefreshing = false
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SolanaStakingPositionsContent(
    state: SolanaStakingPositionsUiState,
    isRefreshing: Boolean = false,
    onRefresh: () -> Unit = {},
    onStake: () -> Unit = {},
    onDeactivate: (String) -> Unit = {},
    onWithdraw: (String) -> Unit = {},
    onMove: (String) -> Unit = {},
) {
    PullToRefreshBox(isRefreshing = isRefreshing, onRefresh = onRefresh) {
        Column(
            modifier =
                Modifier.fillMaxSize()
                    .background(Theme.v2.colors.backgrounds.primary)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalAlignment = CenterHorizontally,
        ) {
            BalanceBanner(
                title = Chain.Solana.raw,
                isLoading = state.isLoading,
                totalValue = state.totalStakedFiatDisplay,
                image = R.drawable.solana,
                isBalanceVisible = state.isBalanceVisible,
            )

            UiSpacer(16.dp)

            Box(modifier = Modifier.fillMaxWidth()) {
                VsTabGroup(index = 0) {
                    tab { VsTab(label = stringResource(R.string.defi_tab_staked), onClick = {}) }
                }
            }

            UiSpacer(16.dp)

            Column(
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                HeaderDeFiWidget(
                    title = stringResource(R.string.solana_staking_total_staked_sol),
                    iconRes = R.drawable.solana,
                    buttonText = stringResource(R.string.solana_delegate_new_validator),
                    onClickAction = onStake,
                    totalAmount = state.totalStakedSolDisplay,
                    totalPrice = state.totalStakedFiatDisplay,
                    isLoading = state.isLoading,
                    isBalanceVisible = state.isBalanceVisible,
                )

                if (state.positions.isNotEmpty()) {
                    StakeAccountsWidget(
                        positions = state.positions,
                        isBalanceVisible = state.isBalanceVisible,
                        onDeactivate = onDeactivate,
                        onWithdraw = onWithdraw,
                        onMove = onMove,
                    )
                }

                state.error?.let {
                    Text(
                        text = it.asString(),
                        style = Theme.brockmann.supplementary.caption,
                        color = Theme.v2.colors.alerts.error,
                    )
                }
            }
        }
    }
}

@Composable
private fun StakeAccountsWidget(
    positions: List<SolanaStakePositionRow>,
    isBalanceVisible: Boolean,
    onDeactivate: (String) -> Unit,
    onWithdraw: (String) -> Unit,
    onMove: (String) -> Unit,
) {
    Column(
        modifier =
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Theme.v2.colors.backgrounds.secondary)
                .border(
                    width = 1.dp,
                    color = Theme.v2.colors.border.light,
                    shape = RoundedCornerShape(16.dp),
                )
                .padding(16.dp)
    ) {
        Text(
            text = stringResource(R.string.solana_stake_accounts),
            style = Theme.brockmann.button.medium.medium,
            color = Theme.v2.colors.text.secondary,
        )

        positions.forEachIndexed { index, row ->
            if (index == 0) {
                UiSpacer(16.dp)
            } else {
                UiSpacer(16.dp)
                UiHorizontalDivider(color = Theme.v2.colors.border.light)
                UiSpacer(16.dp)
            }
            StakeAccountContent(
                row = row,
                isBalanceVisible = isBalanceVisible,
                onDeactivate = { onDeactivate(row.stakePubkey) },
                onWithdraw = { onWithdraw(row.stakePubkey) },
                onMove = { onMove(row.stakePubkey) },
            )
        }
    }
}

@Composable
private fun StakeAccountContent(
    row: SolanaStakePositionRow,
    isBalanceVisible: Boolean,
    onDeactivate: () -> Unit,
    onWithdraw: () -> Unit,
    onMove: () -> Unit,
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            MonogramAvatar(name = row.validatorName)
            UiSpacer(12.dp)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = row.validatorName,
                    style = Theme.brockmann.body.m.medium,
                    color = Theme.v2.colors.text.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                UiSpacer(2.dp)
                Text(
                    text = row.stateLabel.asString(),
                    style = Theme.brockmann.supplementary.caption,
                    color = Theme.v2.colors.alerts.success,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = if (isBalanceVisible) row.stakedDisplay else HIDE_BALANCE_CHARS,
                    style = Theme.brockmann.body.m.medium,
                    color = Theme.v2.colors.text.primary,
                )
                UiSpacer(2.dp)
                Text(
                    text = if (isBalanceVisible) row.stakedFiatDisplay else HIDE_BALANCE_CHARS,
                    style = Theme.brockmann.supplementary.caption,
                    color = Theme.v2.colors.text.tertiary,
                )
            }
        }

        row.apyDisplay?.let {
            UiSpacer(16.dp)
            ApyInfoItem(apy = it)
        }

        UiSpacer(16.dp)
        InfoItem(
            icon = R.drawable.ic_icon_percentage,
            label = stringResource(R.string.solana_rent_reserve),
            value = row.rentReserveDisplay,
        )

        UiSpacer(16.dp)
        UiHorizontalDivider(color = Theme.v2.colors.border.light)
        UiSpacer(16.dp)

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (row.canWithdraw) {
                ActionButton(
                    title = stringResource(R.string.solana_staking_withdraw_cta),
                    icon = R.drawable.ic_circle_plus,
                    background = Theme.v2.colors.primary.accent3,
                    contentColor = Theme.v2.colors.text.primary,
                    onClick = onWithdraw,
                    modifier = Modifier.weight(1f),
                    iconCircleColor = Theme.v2.colors.primary.accent4,
                )
            }
            if (row.canDeactivate) {
                ActionButton(
                    title = stringResource(R.string.solana_staking_unstake_cta),
                    icon = R.drawable.ic_circle_minus,
                    background = Color.Transparent,
                    border = BorderStroke(1.dp, Theme.v2.colors.primary.accent4),
                    contentColor = Theme.v2.colors.text.primary,
                    onClick = onDeactivate,
                    modifier = Modifier.weight(1f),
                    iconCircleColor = Theme.v2.colors.text.tertiary,
                )
                ActionButton(
                    title = stringResource(R.string.solana_staking_move_cta),
                    icon = R.drawable.ic_circle_plus,
                    background = Theme.v2.colors.primary.accent3,
                    contentColor = Theme.v2.colors.text.primary,
                    onClick = onMove,
                    modifier = Modifier.weight(1f),
                    iconCircleColor = Theme.v2.colors.primary.accent4,
                )
            }
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

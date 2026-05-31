package com.vultisig.wallet.ui.screens.cosmosstaking

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vultisig.wallet.data.blockchain.cosmos.staking.CosmosDelegation
import com.vultisig.wallet.data.blockchain.cosmos.staking.CosmosUnbondingDelegation
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.buttons.VsButton
import com.vultisig.wallet.ui.components.buttons.VsButtonSize
import com.vultisig.wallet.ui.components.buttons.VsButtonState
import com.vultisig.wallet.ui.components.buttons.VsButtonVariant
import com.vultisig.wallet.ui.components.v2.scaffold.V2Scaffold
import com.vultisig.wallet.ui.models.cosmosstaking.CosmosStakingPositionsUiState
import com.vultisig.wallet.ui.models.cosmosstaking.CosmosStakingPositionsViewModel
import com.vultisig.wallet.ui.theme.Theme
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Active-delegations view — entry point users land on from the DeFi tab when they tap their Terra /
 * TerraClassic chain. Shows:
 * - **Total Staked card** with claim-all CTA (visible only when there are rewards).
 * - **Per-validator cards** listing the user's active stakes with `[Unstake] [Move] [Stake]` action
 *   row.
 * - **Pending unbondings** section showing in-flight withdrawals with their 21-day completion date.
 * - **Empty state** with a single "Stake" CTA for users who have no active delegations.
 *
 * Styling and microcopy are functional MVP — the Figma-polished version (APY column, Keybase
 * avatars, fiat columns, churned-out badge) lands in a later session.
 */
@Composable
internal fun CosmosStakingPositionsScreen(
    viewModel: CosmosStakingPositionsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    V2Scaffold(title = "${state.ticker.ifEmpty { "Staking" }} positions") {
        Box(modifier = Modifier.fillMaxSize()) {
            PositionsContent(
                state = state,
                onStakeMore = viewModel::stakeMore,
                onClaimAll = viewModel::claimAll,
                onUnstake = viewModel::unstake,
                onMove = viewModel::move,
            )
        }
    }
}

@Composable
private fun PositionsContent(
    state: CosmosStakingPositionsUiState,
    onStakeMore: () -> Unit,
    onClaimAll: () -> Unit,
    onUnstake: (String) -> Unit,
    onMove: (String) -> Unit,
) {
    val errorMessage = state.errorMessage
    when {
        state.isLoading && state.delegations.isEmpty() -> {
            Text(
                text = "Loading positions…",
                style = Theme.brockmann.body.s.medium,
                color = Theme.v2.colors.text.secondary,
                modifier = Modifier.fillMaxWidth().padding(24.dp),
            )
        }
        state.delegations.isEmpty() -> {
            EmptyPositions(ticker = state.ticker, error = errorMessage, onStake = onStakeMore)
        }
        else -> {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                item { TotalStakedCard(state = state, onClaimAll = onClaimAll) }
                item { UiSpacer(size = 12.dp) }

                items(state.delegations, key = { it.validatorAddress }) { delegation ->
                    DelegationCard(
                        delegation = delegation,
                        ticker = state.ticker,
                        onUnstake = { onUnstake(delegation.validatorAddress) },
                        onMove = { onMove(delegation.validatorAddress) },
                        onStakeMore = onStakeMore,
                    )
                    UiSpacer(size = 8.dp)
                }

                if (state.unbondings.isNotEmpty()) {
                    item {
                        UiSpacer(size = 16.dp)
                        Text(
                            text = "Pending unbondings",
                            style = Theme.brockmann.body.s.medium,
                            color = Theme.v2.colors.text.primary,
                        )
                        UiSpacer(size = 8.dp)
                    }
                    items(state.unbondings, key = { it.validatorAddress }) { unbonding ->
                        UnbondingCard(unbonding = unbonding, ticker = state.ticker)
                        UiSpacer(size = 8.dp)
                    }
                }

                if (errorMessage != null) {
                    item {
                        Text(
                            text = errorMessage,
                            style = Theme.brockmann.supplementary.caption,
                            color = Theme.v2.colors.alerts.error,
                            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyPositions(ticker: String, error: String?, onStake: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxWidth().align(Alignment.Center).padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "You have no active ${ticker.ifEmpty { "staking" }} positions yet",
                style = Theme.brockmann.body.s.medium,
                color = Theme.v2.colors.text.secondary,
            )
            UiSpacer(size = 16.dp)
            VsButton(
                label = "Stake ${ticker.ifEmpty { "Token" }}",
                variant = VsButtonVariant.CTA,
                state = VsButtonState.Enabled,
                onClick = onStake,
                modifier = Modifier.fillMaxWidth(),
            )
            if (error != null) {
                UiSpacer(size = 12.dp)
                Text(
                    text = error,
                    style = Theme.brockmann.supplementary.caption,
                    color = Theme.v2.colors.alerts.error,
                )
            }
        }
    }
}

@Composable
private fun TotalStakedCard(state: CosmosStakingPositionsUiState, onClaimAll: () -> Unit) {
    Column(
        modifier =
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .border(
                    width = 1.dp,
                    color = Theme.v2.colors.border.normal,
                    shape = RoundedCornerShape(16.dp),
                )
                .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "Total staked",
            style = Theme.brockmann.supplementary.caption,
            color = Theme.v2.colors.text.secondary,
        )
        Text(
            text = "${state.totalStakedDisplay} ${state.ticker}",
            style = Theme.brockmann.headings.title3,
            color = Theme.v2.colors.text.primary,
        )

        if (state.hasAnyRewards) {
            UiSpacer(size = 4.dp)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        text = "Claimable rewards",
                        style = Theme.brockmann.supplementary.caption,
                        color = Theme.v2.colors.text.secondary,
                    )
                    Text(
                        text = "${state.totalRewardsDisplay} ${state.ticker}",
                        style = Theme.brockmann.body.s.medium,
                        color = Theme.v2.colors.text.primary,
                    )
                }
                VsButton(
                    label = "Claim",
                    variant = VsButtonVariant.CTA,
                    size = VsButtonSize.Small,
                    state = VsButtonState.Enabled,
                    onClick = onClaimAll,
                )
            }
        }
    }
}

@Composable
private fun DelegationCard(
    delegation: CosmosDelegation,
    ticker: String,
    onUnstake: () -> Unit,
    onMove: () -> Unit,
    onStakeMore: () -> Unit,
) {
    Column(
        modifier =
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .border(
                    width = 1.dp,
                    color = Theme.v2.colors.border.normal,
                    shape = RoundedCornerShape(12.dp),
                )
                .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = delegation.validatorAddress,
            style = Theme.brockmann.supplementary.caption,
            color = Theme.v2.colors.text.secondary,
        )
        Text(
            text = "${formatBalance(delegation.balance.amount, ticker)} $ticker",
            style = Theme.brockmann.body.s.medium,
            color = Theme.v2.colors.text.primary,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            VsButton(
                label = "Unstake",
                variant = VsButtonVariant.Secondary,
                size = VsButtonSize.Small,
                state = VsButtonState.Enabled,
                onClick = onUnstake,
                modifier = Modifier.clickable(onClick = onUnstake),
            )
            VsButton(
                label = "Move",
                variant = VsButtonVariant.Secondary,
                size = VsButtonSize.Small,
                state = VsButtonState.Enabled,
                onClick = onMove,
            )
            VsButton(
                label = "Stake more",
                variant = VsButtonVariant.CTA,
                size = VsButtonSize.Small,
                state = VsButtonState.Enabled,
                onClick = onStakeMore,
            )
        }
    }
}

@Composable
private fun UnbondingCard(unbonding: CosmosUnbondingDelegation, ticker: String) {
    Column(
        modifier =
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .border(
                    width = 1.dp,
                    color = Theme.v2.colors.border.normal,
                    shape = RoundedCornerShape(12.dp),
                )
                .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = unbonding.validatorAddress,
            style = Theme.brockmann.supplementary.caption,
            color = Theme.v2.colors.text.secondary,
        )
        unbonding.entries.forEach { entry ->
            val completion =
                runCatching {
                        DateTimeFormatter.ofPattern("yyyy-MM-dd")
                            .withZone(ZoneId.systemDefault())
                            .format(entry.completionTime)
                    }
                    .getOrDefault(entry.completionTime.toString())
            Text(
                text =
                    "${entry.balance.toPlainString()} unbonding base units · unlocks $completion",
                style = Theme.brockmann.body.s.medium,
                color = Theme.v2.colors.text.primary,
            )
        }
    }
}

/**
 * Wire-string base units → display amount. We don't know the chain's decimal here from the
 * delegation wire DTO directly; the ticker is "LUNA" / "LUNC" both of which use 6 decimals (matches
 * `bondDenom = uluna`). If a future chain were added with different decimals this would need to be
 * threaded through from the VM. For now, hard-coded for the Terra family.
 */
private fun formatBalance(baseUnits: String, ticker: String): String {
    val decimals = if (ticker == "LUNA" || ticker == "LUNC") 6 else 6
    return baseUnits.toBigIntegerOrNull()?.let {
        java.math.BigDecimal(it).movePointLeft(decimals).stripTrailingZeros().toPlainString()
    } ?: baseUnits
}

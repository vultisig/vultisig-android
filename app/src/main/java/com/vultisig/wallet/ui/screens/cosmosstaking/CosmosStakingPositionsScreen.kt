package com.vultisig.wallet.ui.screens.cosmosstaking

import androidx.compose.foundation.border
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
import com.vultisig.wallet.data.blockchain.cosmos.staking.CosmosStakePositionRow
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
 * - **Total Staked card** with "Delegate to new validator" CTA + per-row Claim button (when
 *   rewards > 0).
 * - **Per-validator cards** with active/churned-out status badge, staked amount, pending reward,
 *   `[Unstake] [Move] [Stake]` action row. Active validators with no pending unbonding can use all
 *   three; churned-out or unbonding validators have Unstake + Move disabled.
 * - **Pending unbondings** section listing in-flight withdrawals with 21-day completion dates.
 * - **Empty state** with a single "Stake" CTA.
 *
 * Mirrors iOS `CosmosStakeDefiView.swift` (vultisig-ios PR #4432).
 */
@Composable
internal fun CosmosStakingPositionsScreen(
    viewModel: CosmosStakingPositionsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    V2Scaffold(title = "${state.ticker.ifEmpty { "Staking" }} positions") {
        PositionsContent(
            state = state,
            onStakeMore = viewModel::stakeMore,
            onClaimAll = viewModel::claimAll,
            onUnstake = viewModel::unstake,
            onMove = viewModel::move,
        )
    }
}

@Composable
private fun PositionsContent(
    state: CosmosStakingPositionsUiState,
    onStakeMore: () -> Unit,
    onClaimAll: () -> Unit,
    onUnstake: (CosmosStakePositionRow) -> Unit,
    onMove: (CosmosStakePositionRow) -> Unit,
) {
    val errorMessage = state.errorMessage
    Box(modifier = Modifier.fillMaxSize()) {
        when {
            state.isLoading && state.positions.isEmpty() ->
                Text(
                    text = "Loading positions…",
                    style = Theme.brockmann.body.s.medium,
                    color = Theme.v2.colors.text.secondary,
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                )
            state.positions.isEmpty() ->
                EmptyPositions(ticker = state.ticker, error = errorMessage, onStake = onStakeMore)
            else -> {
                LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                    item {
                        TotalStakedCard(
                            ticker = state.ticker,
                            totalStaked = state.totalStaked.toPlainString(),
                            hasAnyClaimableRewards =
                                state.positions.any {
                                    it.pendingReward > java.math.BigDecimal.ZERO
                                },
                            onDelegateToNewValidator = onStakeMore,
                            onClaimAll = onClaimAll,
                        )
                    }
                    item { UiSpacer(size = 12.dp) }

                    items(state.positions, key = { it.validatorAddress }) { position ->
                        PositionRow(
                            position = position,
                            ticker = state.ticker,
                            onUnstake = { onUnstake(position) },
                            onMove = { onMove(position) },
                            onStakeMore = onStakeMore,
                        )
                        UiSpacer(size = 8.dp)
                    }

                    if (state.pendingUnbondings.isNotEmpty()) {
                        item {
                            UiSpacer(size = 16.dp)
                            Text(
                                text = "Pending unbondings",
                                style = Theme.brockmann.body.s.medium,
                                color = Theme.v2.colors.text.primary,
                            )
                            UiSpacer(size = 8.dp)
                        }
                        items(state.pendingUnbondings, key = { it.validatorAddress }) { unbonding ->
                            UnbondingCard(unbonding = unbonding)
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
private fun TotalStakedCard(
    ticker: String,
    totalStaked: String,
    hasAnyClaimableRewards: Boolean,
    onDelegateToNewValidator: () -> Unit,
    onClaimAll: () -> Unit,
) {
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
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "Total Staked $ticker",
            style = Theme.brockmann.supplementary.caption,
            color = Theme.v2.colors.text.secondary,
        )
        Text(
            text = "$totalStaked $ticker",
            style = Theme.brockmann.headings.title3,
            color = Theme.v2.colors.text.primary,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            VsButton(
                label = "Delegate to new validator",
                variant = VsButtonVariant.CTA,
                size = VsButtonSize.Small,
                state = VsButtonState.Enabled,
                onClick = onDelegateToNewValidator,
                modifier = Modifier.weight(1f),
            )
            if (hasAnyClaimableRewards) {
                VsButton(
                    label = "Claim",
                    variant = VsButtonVariant.Secondary,
                    size = VsButtonSize.Small,
                    state = VsButtonState.Enabled,
                    onClick = onClaimAll,
                )
            }
        }
    }
}

@Composable
private fun PositionRow(
    position: CosmosStakePositionRow,
    ticker: String,
    onUnstake: () -> Unit,
    onMove: () -> Unit,
    onStakeMore: () -> Unit,
) {
    val isChurnedOut = position.validatorStatus == CosmosStakePositionRow.ValidatorStatus.ChurnedOut
    val isUnbonding = position.pendingUnbondingUnlockDate != null
    val isLocked = isChurnedOut || isUnbonding

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
        // Validator identity + status badge
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text =
                        position.validatorMoniker.ifEmpty { truncated(position.validatorAddress) },
                    style = Theme.brockmann.body.s.medium,
                    color = Theme.v2.colors.text.primary,
                )
                Text(
                    text = truncated(position.validatorAddress),
                    style = Theme.brockmann.supplementary.caption,
                    color = Theme.v2.colors.text.secondary,
                )
            }
            Text(
                text = if (isChurnedOut) "Churned out" else "Active",
                style = Theme.brockmann.supplementary.caption,
                color =
                    if (isChurnedOut) Theme.v2.colors.alerts.warning
                    else Theme.v2.colors.alerts.success,
            )
        }

        // Staked + reward
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                text = "${position.stakedAmount.toPlainString()} $ticker",
                style = Theme.brockmann.body.s.medium,
                color = Theme.v2.colors.text.primary,
            )
        }
        if (position.pendingReward > java.math.BigDecimal.ZERO) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "Next award",
                    style = Theme.brockmann.supplementary.caption,
                    color = Theme.v2.colors.text.secondary,
                )
                Text(
                    text = "${position.pendingReward.toPlainString()} $ticker",
                    style = Theme.brockmann.body.s.medium,
                    color = Theme.v2.colors.text.primary,
                )
            }
        }

        // Action buttons — Unstake + Move disabled when locked (matches iOS spec).
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            VsButton(
                label = "Unstake",
                variant = VsButtonVariant.Secondary,
                size = VsButtonSize.Small,
                state = if (isLocked) VsButtonState.Disabled else VsButtonState.Enabled,
                onClick = onUnstake,
                modifier = Modifier.weight(1f),
            )
            VsButton(
                label = "Move",
                variant = VsButtonVariant.Secondary,
                size = VsButtonSize.Small,
                state = if (isLocked) VsButtonState.Disabled else VsButtonState.Enabled,
                onClick = onMove,
                modifier = Modifier.weight(1f),
            )
            VsButton(
                label = "Stake",
                variant = VsButtonVariant.CTA,
                size = VsButtonSize.Small,
                state = VsButtonState.Enabled,
                onClick = onStakeMore,
                modifier = Modifier.weight(1f),
            )
        }

        // Per-row unlock footer when this validator has a pending unbonding.
        val unlockDate = position.pendingUnbondingUnlockDate
        if (unlockDate != null) {
            val formatted =
                DateTimeFormatter.ofPattern("MMM d, yyyy")
                    .withZone(ZoneId.systemDefault())
                    .format(unlockDate)
            Text(
                text = "Locked for 21 days — unlocks $formatted",
                style = Theme.brockmann.supplementary.caption,
                color = Theme.v2.colors.text.secondary,
            )
        }
    }
}

@Composable
private fun UnbondingCard(unbonding: CosmosUnbondingDelegation) {
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
            text = truncated(unbonding.validatorAddress),
            style = Theme.brockmann.supplementary.caption,
            color = Theme.v2.colors.text.secondary,
        )
        unbonding.entries.forEach { entry ->
            val completion =
                runCatching {
                        DateTimeFormatter.ofPattern("MMM d, yyyy")
                            .withZone(ZoneId.systemDefault())
                            .format(entry.completionTime)
                    }
                    .getOrDefault(entry.completionTime.toString())
            Text(
                text = "${entry.balance.toPlainString()} base units · unlocks $completion",
                style = Theme.brockmann.body.s.medium,
                color = Theme.v2.colors.text.primary,
            )
        }
    }
}

private fun truncated(address: String): String =
    if (address.length > 14) "${address.substring(0, 8)}…${address.substring(address.length - 4)}"
    else address

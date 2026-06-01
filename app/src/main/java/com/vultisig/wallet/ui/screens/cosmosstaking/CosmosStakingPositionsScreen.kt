package com.vultisig.wallet.ui.screens.cosmosstaking

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import coil.compose.AsyncImage
import com.vultisig.wallet.R
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
    vaultId: String,
    chainId: String,
    viewModel: CosmosStakingPositionsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    androidx.compose.runtime.LaunchedEffect(vaultId, chainId) {
        viewModel.setData(vaultId = vaultId, chainId = chainId)
    }
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
                    text = stringResource(R.string.cosmos_staking_loading_positions),
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
                                text = stringResource(R.string.cosmos_staking_pending_unbondings),
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
                text =
                    stringResource(
                        R.string.cosmos_staking_empty_positions,
                        ticker.ifEmpty { "Token" },
                    ),
                style = Theme.brockmann.body.s.medium,
                color = Theme.v2.colors.text.secondary,
            )
            UiSpacer(size = 16.dp)
            VsButton(
                label =
                    stringResource(
                        R.string.cosmos_staking_delegate_title,
                        ticker.ifEmpty { "Token" },
                    ),
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
            text = stringResource(R.string.cosmos_staking_total_staked, ticker),
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
                label = stringResource(R.string.cosmos_staking_delegate_new_validator),
                variant = VsButtonVariant.CTA,
                size = VsButtonSize.Small,
                state = VsButtonState.Enabled,
                onClick = onDelegateToNewValidator,
                modifier = Modifier.weight(1f),
            )
            if (hasAnyClaimableRewards) {
                VsButton(
                    label = stringResource(R.string.cosmos_staking_action_claim),
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
        // Validator identity (avatar + moniker + address) + status badge
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                ValidatorAvatar(
                    avatarUrl = position.validatorAvatarUrl,
                    monogram =
                        position.validatorMoniker
                            .ifEmpty { position.validatorAddress }
                            .take(1)
                            .uppercase(),
                    size = 36.dp,
                )
                UiSpacer(size = 8.dp)
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text =
                            position.validatorMoniker.ifEmpty {
                                truncated(position.validatorAddress)
                            },
                        style = Theme.brockmann.body.s.medium,
                        color = Theme.v2.colors.text.primary,
                    )
                    Text(
                        text = truncated(position.validatorAddress),
                        style = Theme.brockmann.supplementary.caption,
                        color = Theme.v2.colors.text.secondary,
                    )
                }
            }
            Text(
                text =
                    stringResource(
                        if (isChurnedOut) R.string.cosmos_staking_validator_churned_out
                        else R.string.cosmos_staking_validator_active
                    ),
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

        // APY row — hidden when null (matches iOS / Windows behavior under chain-APY fan-out
        // failure). Value is fractional (0.05 = 5%); render as a percentage with 2 decimals.
        val apy = position.apyPercent
        if (apy != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = stringResource(R.string.cosmos_staking_apy),
                    style = Theme.brockmann.supplementary.caption,
                    color = Theme.v2.colors.text.secondary,
                )
                Text(
                    text =
                        "${apy.multiply(java.math.BigDecimal(100)).setScale(2, java.math.RoundingMode.HALF_UP).toPlainString()}%",
                    style = Theme.brockmann.body.s.medium,
                    color = Theme.v2.colors.alerts.success,
                )
            }
        }

        if (position.pendingReward > java.math.BigDecimal.ZERO) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = stringResource(R.string.cosmos_staking_next_award),
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
                label = stringResource(R.string.cosmos_staking_action_undelegate),
                variant = VsButtonVariant.Secondary,
                size = VsButtonSize.Small,
                state = if (isLocked) VsButtonState.Disabled else VsButtonState.Enabled,
                onClick = onUnstake,
                modifier = Modifier.weight(1f),
            )
            VsButton(
                label = stringResource(R.string.cosmos_staking_action_redelegate),
                variant = VsButtonVariant.Secondary,
                size = VsButtonSize.Small,
                state = if (isLocked) VsButtonState.Disabled else VsButtonState.Enabled,
                onClick = onMove,
                modifier = Modifier.weight(1f),
            )
            VsButton(
                label = stringResource(R.string.cosmos_staking_action_delegate),
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
                text = stringResource(R.string.cosmos_staking_unbonding_lock_notice, 21, formatted),
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

/**
 * Validator avatar — Keybase profile picture when resolved, otherwise a deterministic colored
 * monogram (first character of the moniker / address). Mirrors iOS `KeybaseAvatarView` + Windows
 * `ValidatorAvatar` colored-initial fallback.
 */
@Composable
private fun ValidatorAvatar(
    avatarUrl: String?,
    monogram: String,
    size: androidx.compose.ui.unit.Dp,
) {
    if (avatarUrl != null) {
        AsyncImage(
            model = avatarUrl,
            contentDescription = null,
            modifier = Modifier.size(size).clip(CircleShape),
        )
    } else {
        Box(
            modifier = Modifier.size(size).clip(CircleShape).background(monogramColor(monogram)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = monogram,
                style = Theme.brockmann.supplementary.caption,
                color = Theme.v2.colors.text.primary,
            )
        }
    }
}

/**
 * Deterministic background color keyed off the monogram so the same validator is always the same
 * hue.
 */
private fun monogramColor(monogram: String): androidx.compose.ui.graphics.Color {
    val palette =
        listOf(
            androidx.compose.ui.graphics.Color(0xFF2D4BF3),
            androidx.compose.ui.graphics.Color(0xFF0A8A6B),
            androidx.compose.ui.graphics.Color(0xFF8A2BE2),
            androidx.compose.ui.graphics.Color(0xFFB8860B),
            androidx.compose.ui.graphics.Color(0xFFC2410C),
            androidx.compose.ui.graphics.Color(0xFF0E7490),
        )
    val index = if (monogram.isEmpty()) 0 else (monogram[0].code % palette.size)
    return palette[index]
}

private fun truncated(address: String): String =
    if (address.length > 14) "${address.substring(0, 8)}…${address.substring(address.length - 4)}"
    else address

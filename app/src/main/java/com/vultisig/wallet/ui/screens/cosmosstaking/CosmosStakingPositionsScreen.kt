package com.vultisig.wallet.ui.screens.cosmosstaking

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import coil.compose.AsyncImage
import com.vultisig.wallet.R
import com.vultisig.wallet.data.blockchain.cosmos.staking.CosmosStakePositionRow
import com.vultisig.wallet.data.blockchain.cosmos.staking.CosmosUnbondingDelegation
import com.vultisig.wallet.data.models.getCoinLogo
import com.vultisig.wallet.ui.components.UiGradientHorizontalDivider
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.buttons.VsButton
import com.vultisig.wallet.ui.components.buttons.VsButtonSize
import com.vultisig.wallet.ui.components.buttons.VsButtonState
import com.vultisig.wallet.ui.components.buttons.VsButtonVariant
import com.vultisig.wallet.ui.components.library.UiPlaceholderLoader
import com.vultisig.wallet.ui.components.v2.tab.VsTab
import com.vultisig.wallet.ui.components.v2.tab.VsTabGroup
import com.vultisig.wallet.ui.models.cosmosstaking.CosmosStakingPositionsUiState
import com.vultisig.wallet.ui.models.cosmosstaking.CosmosStakingPositionsViewModel
import com.vultisig.wallet.ui.screens.RegisterChainDashboardTopBarAction
import com.vultisig.wallet.ui.screens.v2.defi.NoPositionsContainer
import com.vultisig.wallet.ui.screens.v2.defi.PositionsSelectionDialog
import com.vultisig.wallet.ui.theme.Theme
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val COSMOS_STAKING_TABS = listOf(com.vultisig.wallet.ui.screens.v2.defi.DeFiTab.STAKED)

/**
 * LUNA / LUNC active-delegations view — reuses the shared DeFi-chain chrome (balance banner +
 * "Staked" tab + Manage Positions) so it matches Maya/Tron and iOS. Inside the Staked tab:
 * - **Total Staked card** with "Delegate to New Validator" CTA + Claim button (when rewards > 0).
 * - **Active Delegations** per-validator cards (avatar, Active/Churned-out badge, staked, next
 *   award, `[Unstake] [Move] [Stake]`).
 * - **Pending Unbondings** section.
 * - When the position is disabled in Manage Positions → the shared "No positions selected" state.
 *
 * Mirrors iOS `CosmosStakeDefiView.swift` (vultisig-ios PR #4432).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CosmosStakingPositionsScreen(
    vaultId: String,
    chainId: String,
    viewModel: CosmosStakingPositionsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    // The VM owns the refresh flag: a cache-seeded refresh keeps `isLoading` false, so deriving the
    // spinner from `isLoading` on the screen would leave it spinning forever after the first warm
    // load. Collect it directly instead.
    val isRefreshing by viewModel.isRefreshing.collectAsState()

    LaunchedEffect(vaultId, chainId) { viewModel.setData(vaultId = vaultId, chainId = chainId) }

    // Reload when the screen comes back to the foreground — e.g. after returning from a completed
    // delegate / undelegate / redelegate / claim — so Total Staked and the positions reflect the tx
    // without a manual pull. The first resume is a no-op in the VM (#4815).
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.onScreenResumed() }

    RegisterChainDashboardTopBarAction(
        icon = R.drawable.ic_shapes_plus_x_square_circle,
        onClick = { viewModel.setPositionSelectionDialogVisibility(true) },
    )

    CosmosStakingPositionsContent(
        state = state,
        chainId = chainId,
        isRefreshing = isRefreshing,
        onRefresh = viewModel::refresh,
        onManagePositions = { viewModel.setPositionSelectionDialogVisibility(true) },
        onClaim = viewModel::claimAll,
        onDelegateToNewValidator = viewModel::stakeMore,
        onUnstake = viewModel::unstake,
        onMove = viewModel::move,
        onStakeMore = viewModel::stakeMore,
        onPositionSelectionChange = viewModel::onPositionSelectionChange,
        onPositionSelectionDone = viewModel::onPositionSelectionDone,
        onDismissDialog = { viewModel.setPositionSelectionDialogVisibility(false) },
    )
}

/**
 * Stateless body of the staking-positions screen — split out from the Hilt/ViewModel-bound
 * [CosmosStakingPositionsScreen] so the loaded / loading / empty states are previewable in
 * isolation (the public composable can't be, since it drives navigation and registers top-bar
 * actions).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CosmosStakingPositionsContent(
    state: CosmosStakingPositionsUiState,
    chainId: String,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    onManagePositions: () -> Unit,
    onClaim: () -> Unit,
    onDelegateToNewValidator: () -> Unit,
    onUnstake: (CosmosStakePositionRow) -> Unit,
    onMove: (CosmosStakePositionRow) -> Unit,
    onStakeMore: () -> Unit,
    onPositionSelectionChange: (String, Boolean) -> Unit,
    onPositionSelectionDone: () -> Unit,
    onDismissDialog: () -> Unit,
) {
    PullToRefreshBox(isRefreshing = isRefreshing, onRefresh = onRefresh) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.fillMaxSize().background(Theme.v2.colors.backgrounds.primary)
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    // Banner + tab row scroll with the list as part of the whole screen (mirrors
                    // iOS) rather than staying pinned above it (#4761).
                    item {
                        CosmosStakingBalanceBanner(
                            chainName = chainId,
                            coinLogo = state.coinLogo,
                            balanceFiat = state.totalAmountPrice,
                            isBalanceVisible = state.isBalanceVisible,
                        )
                    }

                    // Single manage-positions control: the ChainDashboard top-bar action above. The
                    // inline edit-chains button that used to sit here duplicated it (#4821).
                    item {
                        VsTabGroup(index = 0) {
                            COSMOS_STAKING_TABS.forEach { tab ->
                                tab {
                                    VsTab(label = stringResource(tab.displayNameRes), onClick = {})
                                }
                            }
                        }
                    }

                    if (!state.isPositionEnabled) {
                        item { NoPositionsContainer(onManagePositionsClick = onManagePositions) }
                    } else {
                        item {
                            TotalStakedCard(
                                coinLogo = state.coinLogo,
                                ticker = state.ticker,
                                totalStaked = formatStakeAmount(state.totalStaked),
                                totalFiat = state.totalStakedFiat,
                                hasClaimableRewards = state.hasClaimableRewards,
                                onClaim = onClaim,
                                onDelegateToNewValidator = onDelegateToNewValidator,
                            )
                        }

                        if (state.positions.isNotEmpty()) {
                            item {
                                Text(
                                    text =
                                        stringResource(R.string.cosmos_staking_active_delegations),
                                    style = Theme.brockmann.body.s.medium,
                                    color = Theme.v2.colors.text.secondary,
                                )
                            }
                            items(
                                state.positions,
                                // Prefix the key: a validator can appear in BOTH this list and the
                                // pending-unbondings list below (partial unstake keeps the rest
                                // delegated), and LazyColumn keys must be unique across the whole
                                // list — a bare validatorAddress would collide and crash on scroll.
                                key = { "position-${it.validatorAddress}" },
                            ) { position ->
                                PositionRow(
                                    position = position,
                                    ticker = state.ticker,
                                    fiat = position.stakedFiatDisplay,
                                    onUnstake = { onUnstake(position) },
                                    onMove = { onMove(position) },
                                    onStakeMore = onStakeMore,
                                )
                            }
                        } else if (state.isLoading) {
                            // First fetch in flight with nothing cached: show skeleton placeholder
                            // cards instead of plain text, which reads as "stuck" for the second or
                            // two the fan-out takes (#4815).
                            items(count = 2, key = { "skeleton-$it" }) { StakingPositionSkeleton() }
                        } else {
                            // Fetch settled with no delegations: empty-state (the screen would
                            // otherwise render only the zeroed Total Staked card).
                            item {
                                Text(
                                    text =
                                        stringResource(
                                            R.string.cosmos_staking_empty_positions,
                                            state.ticker,
                                        ),
                                    style = Theme.brockmann.body.s.medium,
                                    color = Theme.v2.colors.text.secondary,
                                )
                            }
                        }

                        if (state.pendingUnbondings.isNotEmpty()) {
                            item {
                                Text(
                                    text =
                                        stringResource(R.string.cosmos_staking_pending_unbondings),
                                    style = Theme.brockmann.body.s.medium,
                                    color = Theme.v2.colors.text.primary,
                                )
                            }
                            items(
                                state.pendingUnbondings,
                                key = { "unbonding-${it.validatorAddress}" },
                            ) { unbonding ->
                                UnbondingCard(unbonding = unbonding)
                            }
                        }
                    }

                    val errorMessage = state.errorMessage
                    if (errorMessage != null) {
                        item {
                            Text(
                                text = errorMessage,
                                style = Theme.brockmann.supplementary.caption,
                                color = Theme.v2.colors.alerts.error,
                            )
                        }
                    }
                }
            }

            if (state.showPositionSelectionDialog) {
                val searchTextFieldState = remember { TextFieldState() }
                PositionsSelectionDialog(
                    stakePositions = state.stakePositionsDialog,
                    selectedPositions = state.tempSelectedPositions,
                    searchTextFieldState = searchTextFieldState,
                    onPositionSelectionChange = onPositionSelectionChange,
                    onDoneClick = onPositionSelectionDone,
                    onCancelClick = onDismissDialog,
                )
            }
        }
    }
}

@Composable
private fun TotalStakedCard(
    coinLogo: String,
    ticker: String,
    totalStaked: String,
    totalFiat: String,
    hasClaimableRewards: Boolean,
    onClaim: () -> Unit,
    onDelegateToNewValidator: () -> Unit,
) {
    Column(
        modifier =
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .border(
                    width = 1.dp,
                    color = Theme.v2.colors.border.light,
                    shape = RoundedCornerShape(16.dp),
                )
                .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Coin logo + "Total Staked LUNC" headline + amount + fiat subtitle (iOS layout).
        Row(verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(
                model = getCoinLogo(coinLogo),
                contentDescription = null,
                modifier = Modifier.size(40.dp).clip(CircleShape),
            )
            UiSpacer(size = 12.dp)
            Column {
                Text(
                    text = stringResource(R.string.cosmos_staking_total_staked, ticker),
                    style = Theme.brockmann.supplementary.caption,
                    color = Theme.v2.colors.text.secondary,
                )
                Text(
                    text = "$totalStaked $ticker",
                    style = Theme.brockmann.headings.title2,
                    color = Theme.v2.colors.text.primary,
                )
                Text(
                    text = totalFiat,
                    style = Theme.brockmann.supplementary.caption,
                    color = Theme.v2.colors.text.secondary,
                )
            }
        }

        UiGradientHorizontalDivider()

        // Claim button only when there are pending rewards to withdraw (iOS spec).
        if (hasClaimableRewards) {
            VsButton(
                label = stringResource(R.string.cosmos_staking_action_claim),
                variant = VsButtonVariant.Secondary,
                size = VsButtonSize.Medium,
                state = VsButtonState.Enabled,
                onClick = onClaim,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        VsButton(
            label = stringResource(R.string.cosmos_staking_delegate_new_validator),
            variant = VsButtonVariant.CTA,
            size = VsButtonSize.Medium,
            state = VsButtonState.Enabled,
            onClick = onDelegateToNewValidator,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun PositionRow(
    position: CosmosStakePositionRow,
    ticker: String,
    fiat: String,
    onUnstake: () -> Unit,
    onMove: () -> Unit,
    onStakeMore: () -> Unit,
) {
    val isChurnedOut = position.validatorStatus == CosmosStakePositionRow.ValidatorStatus.ChurnedOut
    val isUnbonding = position.pendingUnbondingUnlockDate != null
    // Unstake is blocked ONLY once the validator hits cosmos-sdk's MAX_ENTRIES (7) concurrent
    // unbonding entries — a partial unstake with headroom must stay enabled (matches canUnstake).
    // Move/Redelegate is blocked only while an unbonding is pending; a churned-out (jailed/slashed)
    // validator must remain movable since that is the only instant escape (matches canMove).
    val isUnstakeLocked = position.maxUnbondingEntriesReached
    val isMoveLocked = isUnbonding

    Column(
        modifier =
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .border(
                    width = 1.dp,
                    color = Theme.v2.colors.border.light,
                    shape = RoundedCornerShape(12.dp),
                )
                .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
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
                    size = 40.dp,
                    colorKey = position.validatorAddress,
                )
                UiSpacer(size = 12.dp)
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text =
                            position.validatorMoniker.ifEmpty {
                                truncated(position.validatorAddress)
                            },
                        style = Theme.brockmann.body.m.medium,
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

        // Staked amount (left) + fiat value (right) — iOS "Staked: 1 LUNC" / "$0.00".
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text =
                    stringResource(
                        R.string.cosmos_staking_staked_row_amount,
                        formatStakeAmount(position.stakedAmount),
                        ticker,
                    ),
                style = Theme.brockmann.body.m.medium,
                color = Theme.v2.colors.text.primary,
            )
            Text(
                text = fiat,
                style = Theme.brockmann.body.s.medium,
                color = Theme.v2.colors.text.secondary,
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

        UiGradientHorizontalDivider()

        // 🏆 Next Award (left) + pending reward (right) — iOS shows this even at tiny values.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "🏆",
                    style = Theme.brockmann.supplementary.caption,
                    color = Theme.v2.colors.text.secondary,
                )
                UiSpacer(size = 6.dp)
                Text(
                    text = stringResource(R.string.cosmos_staking_next_award),
                    style = Theme.brockmann.supplementary.caption,
                    color = Theme.v2.colors.text.secondary,
                )
            }
            Text(
                text = "${formatStakeAmount(position.pendingReward)} $ticker",
                style = Theme.brockmann.body.s.medium,
                color = Theme.v2.colors.text.primary,
                maxLines = 1,
            )
        }

        // Action buttons — Unstake blocked only at the MAX_ENTRIES unbonding cap (churned-out
        // validators must remain exitable). Move blocked only while an unbonding is pending; it
        // stays enabled for a churned-out source so the user can escape a jailed/slashed validator.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            VsButton(
                label = stringResource(R.string.cosmos_staking_action_undelegate),
                variant = VsButtonVariant.Secondary,
                size = VsButtonSize.Small,
                state = if (isUnstakeLocked) VsButtonState.Disabled else VsButtonState.Enabled,
                onClick = onUnstake,
                modifier = Modifier.weight(1f),
            )
            VsButton(
                label = stringResource(R.string.cosmos_staking_action_redelegate),
                variant = VsButtonVariant.Secondary,
                size = VsButtonSize.Small,
                state = if (isMoveLocked) VsButtonState.Disabled else VsButtonState.Enabled,
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

/**
 * Placeholder delegation card shown during the first cold load (#4815). Mirrors the [PositionRow]
 * silhouette — avatar + two text lines + an amount bar — using the shared [UiPlaceholderLoader] so
 * the wait reads as "loading" and stays visually consistent with the other DeFi tabs.
 */
@Composable
internal fun StakingPositionSkeleton() {
    Column(
        modifier =
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .border(
                    width = 1.dp,
                    color = Theme.v2.colors.border.light,
                    shape = RoundedCornerShape(12.dp),
                )
                .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            UiPlaceholderLoader(modifier = Modifier.size(40.dp).clip(CircleShape))
            UiSpacer(size = 12.dp)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                UiPlaceholderLoader(modifier = Modifier.fillMaxWidth(0.5f).height(14.dp))
                UiPlaceholderLoader(modifier = Modifier.fillMaxWidth(0.3f).height(12.dp))
            }
        }
        UiPlaceholderLoader(modifier = Modifier.fillMaxWidth().height(12.dp))
        UiPlaceholderLoader(modifier = Modifier.fillMaxWidth(0.6f).height(12.dp))
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
                    color = Theme.v2.colors.border.light,
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
                text =
                    stringResource(
                        R.string.cosmos_staking_unbonding_entry_format,
                        entry.balance.toPlainString(),
                        completion,
                    ),
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
internal fun ValidatorAvatar(
    avatarUrl: String?,
    monogram: String,
    size: androidx.compose.ui.unit.Dp,
    // Full, stable identifier (valoper address / moniker) used to pick the fallback hue. Defaults
    // to the monogram for callers without a richer key, but passing the full key avoids the
    // one-colour-per-leading-letter collisions on a screen with several delegations.
    colorKey: String = monogram,
) {
    if (avatarUrl != null) {
        AsyncImage(
            model = avatarUrl,
            contentDescription = null,
            modifier = Modifier.size(size).clip(CircleShape),
        )
    } else {
        Box(
            modifier = Modifier.size(size).clip(CircleShape).background(monogramColor(colorKey)),
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
 * Deterministic background color keyed off the full identifier so the same validator is always the
 * same hue. Hashing the whole string (not just the first character) keeps validators that share a
 * leading letter from collapsing onto the same colour on a screen with several delegations.
 */
internal fun monogramColor(key: String): androidx.compose.ui.graphics.Color {
    val palette =
        listOf(
            androidx.compose.ui.graphics.Color(0xFF2D4BF3),
            androidx.compose.ui.graphics.Color(0xFF0A8A6B),
            androidx.compose.ui.graphics.Color(0xFF8A2BE2),
            androidx.compose.ui.graphics.Color(0xFFB8860B),
            androidx.compose.ui.graphics.Color(0xFFC2410C),
            androidx.compose.ui.graphics.Color(0xFF0E7490),
            androidx.compose.ui.graphics.Color(0xFFBE185D),
            androidx.compose.ui.graphics.Color(0xFF15803D),
            androidx.compose.ui.graphics.Color(0xFF6D28D9),
            androidx.compose.ui.graphics.Color(0xFF0369A1),
            androidx.compose.ui.graphics.Color(0xFFA16207),
            androidx.compose.ui.graphics.Color(0xFF9D174D),
        )
    if (key.isEmpty()) return palette[0]
    return palette[(key.hashCode() % palette.size + palette.size) % palette.size]
}

internal fun truncated(address: String): String =
    if (address.length > 14) "${address.substring(0, 8)}…${address.substring(address.length - 4)}"
    else address

/** iOS Terra hero-banner teal (`#34E6BF`) — gradient fill + border tint. */
private val CosmosBannerTeal = androidx.compose.ui.graphics.Color(0xFF34E6BF)

/**
 * LUNA / LUNC hero banner — mirrors iOS `DefiChainBalanceView`: a teal-tinted gradient card with
 * the chain name, a "Balance" label, and the fiat total, plus the coin logo encircled by two faint
 * rings bleeding off the trailing edge. Replaces the generic referral banner the shared
 * [BalanceBanner] would otherwise show.
 */
@Composable
private fun CosmosStakingBalanceBanner(
    chainName: String,
    coinLogo: String,
    balanceFiat: String,
    isBalanceVisible: Boolean,
) {
    Box(
        modifier =
            Modifier.fillMaxWidth()
                .height(118.dp)
                .clip(RoundedCornerShape(16.dp))
                .border(
                    width = 1.dp,
                    color = CosmosBannerTeal.copy(alpha = 0.17f),
                    shape = RoundedCornerShape(16.dp),
                )
                .background(
                    androidx.compose.ui.graphics.Brush.verticalGradient(
                        listOf(
                            CosmosBannerTeal.copy(alpha = 0.09f),
                            androidx.compose.ui.graphics.Color.Transparent,
                        )
                    )
                )
    ) {
        // Trailing decorative: coin logo + two concentric rings, bleeding off the edge at 60%.
        Box(
            modifier =
                Modifier.align(Alignment.CenterEnd).padding(end = 12.dp).alpha(0.6f).size(120.dp),
            contentAlignment = Alignment.Center,
        ) {
            AsyncImage(
                model = getCoinLogo(coinLogo),
                contentDescription = null,
                modifier = Modifier.size(92.dp).clip(CircleShape),
            )
            Box(
                modifier =
                    Modifier.size(92.dp)
                        .border(
                            2.dp,
                            Theme.v2.colors.alerts.success.copy(alpha = 0.4f),
                            CircleShape,
                        )
            )
            Box(
                modifier =
                    Modifier.size(120.dp)
                        .border(
                            1.dp,
                            Theme.v2.colors.alerts.success.copy(alpha = 0.25f),
                            CircleShape,
                        )
            )
        }

        Column(modifier = Modifier.padding(start = 16.dp, top = 20.dp)) {
            Text(
                text = chainName,
                style = Theme.brockmann.body.l.medium,
                color = Theme.v2.colors.text.primary,
            )
            UiSpacer(size = 8.dp)
            Text(
                text = stringResource(R.string.select_chain_balance_title),
                style = Theme.brockmann.supplementary.caption,
                color = Theme.v2.colors.text.primary,
            )
            UiSpacer(size = 4.dp)
            Text(
                text = if (isBalanceVisible) balanceFiat else "• • • • • •",
                style = Theme.satoshi.price.title1,
                color = Theme.v2.colors.text.primary,
            )
        }
    }
}

/**
 * Whole-token amount formatter shared across the staking cards. Mirrors iOS
 * `CosmosStakeDefiView.formatAmount` (decimal style, min 0 / max 6 fraction digits, half-even
 * rounding, locale grouping) so values like a raw `0.005657133717398625` reward render as
 * `0.005657` instead of overflowing the row.
 */
private val stakeAmountFormat: DecimalFormat =
    DecimalFormat("#,##0.######").apply { roundingMode = RoundingMode.HALF_EVEN }

internal fun formatStakeAmount(value: BigDecimal): String = stakeAmountFormat.format(value)

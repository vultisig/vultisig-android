package com.vultisig.wallet.ui.screens.v2.defi.solana

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Alignment.Companion.CenterHorizontally
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.vultisig.wallet.R
import com.vultisig.wallet.data.blockchain.solana.staking.SolanaStakeState
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.VaultId
import com.vultisig.wallet.ui.components.UiHorizontalDivider
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.library.UiPlaceholderLoader
import com.vultisig.wallet.ui.components.v2.tab.VsTab
import com.vultisig.wallet.ui.components.v2.tab.VsTabGroup
import com.vultisig.wallet.ui.models.solanastaking.SolanaStakePositionRow
import com.vultisig.wallet.ui.models.solanastaking.SolanaStakingPositionsUiState
import com.vultisig.wallet.ui.models.solanastaking.SolanaStakingPositionsViewModel
import com.vultisig.wallet.ui.screens.cosmosstaking.ValidatorAvatar
import com.vultisig.wallet.ui.screens.v2.defi.ActionButton
import com.vultisig.wallet.ui.screens.v2.defi.ApyInfoItem
import com.vultisig.wallet.ui.screens.v2.defi.DeFiTab
import com.vultisig.wallet.ui.screens.v2.defi.FIAT_VALUE_UNAVAILABLE
import com.vultisig.wallet.ui.screens.v2.defi.HeaderDeFiWidget
import com.vultisig.wallet.ui.screens.v2.defi.InfoItem
import com.vultisig.wallet.ui.screens.v2.defi.ManagePositionsButton
import com.vultisig.wallet.ui.theme.Theme
import com.vultisig.wallet.ui.utils.asString

private val HIDE_BALANCE_CHARS = "• ".repeat(6).trim()

/** Earn leads, matching the design: yield is the reason most users open this tab. */
private val SOLANA_DEFI_TABS = listOf(DeFiTab.EARN, DeFiTab.STAKED)

/** Entry point for the Solana native-staking positions screen. */
@Composable
internal fun SolanaStakingPositionsScreen(
    vaultId: VaultId,
    viewModel: SolanaStakingPositionsViewModel = hiltViewModel(),
    kaminoViewModel: KaminoEarnViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val kaminoState by kaminoViewModel.state.collectAsState()
    var selectedTab by rememberSaveable { mutableStateOf(DeFiTab.EARN) }

    LifecycleResumeEffect(vaultId) {
        viewModel.setData(vaultId)
        kaminoViewModel.setData(vaultId)
        onPauseOrDispose {}
    }

    // The header banner is the chain's total, so it needs both tabs' figures. Earn owns its own
    // load, so its total is handed to the staking view-model as it resolves rather than being
    // summed in the composition, where the user's currency format isn't available.
    LaunchedEffect(kaminoState.totalFiatValue) {
        viewModel.onKaminoTotalChanged(kaminoState.totalFiatValue)
    }

    SolanaStakingPositionsContent(
        state = state,
        kaminoState = kaminoState,
        selectedTab = selectedTab,
        onTabSelected = { selectedTab = it },
        isRefreshing = state.isReloading,
        onRefresh = {
            viewModel.refresh()
            kaminoViewModel.refresh()
        },
        onStake = viewModel::onStake,
        onMove = viewModel::onMove,
        onFinishMove = viewModel::onFinishMove,
        onDeactivate = viewModel::onDeactivate,
        onWithdraw = viewModel::onWithdraw,
        onKaminoDeposit = { kaminoViewModel.onDeposit(vaultId, it) },
        onKaminoWithdraw = { kaminoViewModel.onWithdraw(vaultId, it) },
        onManagePositions = kaminoViewModel::openPicker,
    )

    if (kaminoState.isShowingPicker) {
        KaminoVaultPickerSheet(
            selected = kaminoState.pendingSelection,
            onToggle = kaminoViewModel::onVaultToggled,
            onDone = kaminoViewModel::savePicker,
            onDismiss = kaminoViewModel::closePicker,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SolanaStakingPositionsContent(
    state: SolanaStakingPositionsUiState,
    kaminoState: KaminoEarnUiModel = KaminoEarnUiModel(),
    selectedTab: DeFiTab = DeFiTab.EARN,
    onTabSelected: (DeFiTab) -> Unit = {},
    isRefreshing: Boolean = false,
    onRefresh: () -> Unit = {},
    onStake: () -> Unit = {},
    onMove: (String) -> Unit = {},
    onFinishMove: (String) -> Unit = {},
    onDeactivate: (String) -> Unit = {},
    onWithdraw: (String) -> Unit = {},
    onKaminoDeposit: (String) -> Unit = {},
    onKaminoWithdraw: (String) -> Unit = {},
    onManagePositions: () -> Unit = {},
) {
    PullToRefreshBox(isRefreshing = isRefreshing, onRefresh = onRefresh) {
        // One scroll surface for the whole screen: the banner and tab row scroll away with the
        // content instead of staying pinned above it, mirroring iOS (#4761).
        Column(
            modifier =
                Modifier.fillMaxSize()
                    .background(Theme.v2.colors.backgrounds.primary)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalAlignment = CenterHorizontally,
        ) {
            SolanaHeaderBanner(
                totalValue = state.chainTotalFiatDisplay,
                // Both halves gate the banner: showing the staking total the moment it lands would
                // print a figure that then jumps as Earn resolves.
                isLoading = state.isLoading || kaminoState.isLoading,
                isBalanceVisible = state.isBalanceVisible,
            )

            UiSpacer(16.dp)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                VsTabGroup(index = SOLANA_DEFI_TABS.indexOf(selectedTab).coerceAtLeast(0)) {
                    SOLANA_DEFI_TABS.forEach { tab ->
                        tab {
                            VsTab(
                                label = stringResource(tab.displayNameRes),
                                onClick = { onTabSelected(tab) },
                            )
                        }
                    }
                }

                // Only Earn has anything to manage; native staking has no per-position opt-in.
                if (selectedTab == DeFiTab.EARN) {
                    ManagePositionsButton(onClick = onManagePositions)
                }
            }

            UiSpacer(16.dp)

            if (selectedTab == DeFiTab.EARN) {
                KaminoEarnTabContent(
                    state = kaminoState,
                    onDeposit = onKaminoDeposit,
                    onWithdraw = onKaminoWithdraw,
                    emptyState = { KaminoEarnEmptyState() },
                )
            } else {
                StakedTabContent(
                    state = state,
                    onStake = onStake,
                    onMove = onMove,
                    onFinishMove = onFinishMove,
                    onDeactivate = onDeactivate,
                    onWithdraw = onWithdraw,
                )
            }
        }
    }
}

/** Native SOL staking: the total, then one card per stake account. */
@Composable
private fun StakedTabContent(
    state: SolanaStakingPositionsUiState,
    onStake: () -> Unit,
    onMove: (String) -> Unit,
    onFinishMove: (String) -> Unit,
    onDeactivate: (String) -> Unit,
    onWithdraw: (String) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
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
                onFinishMove = onFinishMove,
                onStake = onStake,
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

/** Shown until the user switches a Kamino vault on under Manage positions. */
@Composable
private fun KaminoEarnEmptyState() {
    Column(
        modifier =
            Modifier.fillMaxWidth()
                .clip(Theme.v2.radius.xl)
                .background(Theme.v2.colors.backgrounds.secondary)
                .border(
                    width = 1.dp,
                    color = Theme.v2.colors.border.light,
                    shape = Theme.v2.radius.xl,
                )
                .padding(16.dp),
        horizontalAlignment = CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.kamino_earn_empty_title),
            style = Theme.brockmann.body.m.medium,
            color = Theme.v2.colors.text.primary,
        )

        UiSpacer(4.dp)

        Text(
            text = stringResource(R.string.kamino_earn_empty_description),
            style = Theme.brockmann.supplementary.caption,
            color = Theme.v2.colors.text.tertiary,
        )
    }
}

@Composable
private fun StakeAccountsWidget(
    positions: List<SolanaStakePositionRow>,
    isBalanceVisible: Boolean,
    onDeactivate: (String) -> Unit,
    onWithdraw: (String) -> Unit,
    onMove: (String) -> Unit,
    onFinishMove: (String) -> Unit,
    onStake: () -> Unit,
) {
    // The header is a plain label above the list; each stake account is its own separate card with
    // spacing between them (rather than a single shared card with dividers), so cards read as
    // distinct items.
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = stringResource(R.string.solana_stake_accounts),
            style = Theme.brockmann.button.medium.medium,
            color = Theme.v2.colors.text.secondary,
        )

        positions.forEach { row ->
            Column(
                modifier =
                    Modifier.fillMaxWidth()
                        .clip(Theme.v2.radius.xl)
                        .background(Theme.v2.colors.backgrounds.secondary)
                        .border(
                            width = 1.dp,
                            color = Theme.v2.colors.border.light,
                            shape = Theme.v2.radius.xl,
                        )
                        .padding(16.dp)
            ) {
                StakeAccountContent(
                    row = row,
                    isBalanceVisible = isBalanceVisible,
                    onDeactivate = { onDeactivate(row.stakePubkey) },
                    onWithdraw = { onWithdraw(row.stakePubkey) },
                    onMove = { onMove(row.stakePubkey) },
                    onFinishMove = { onFinishMove(row.stakePubkey) },
                    onStake = onStake,
                )
            }
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
    onFinishMove: () -> Unit,
    onStake: () -> Unit,
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ValidatorAvatar(
                avatarUrl = row.validatorLogoUrl,
                monogram = row.validatorName.take(1),
                size = 40.dp,
                colorKey = row.stakePubkey,
            )
            UiSpacer(12.dp)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = row.validatorName,
                    style = Theme.brockmann.body.m.medium,
                    color = Theme.v2.colors.text.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (row.validatorAddressDisplay.isNotEmpty()) {
                    UiSpacer(2.dp)
                    Text(
                        text = row.validatorAddressDisplay,
                        style = Theme.brockmann.supplementary.caption,
                        color = Theme.v2.colors.text.tertiary,
                    )
                }
                UiSpacer(2.dp)
                Text(
                    text = row.stateLabel.asString(),
                    style = Theme.brockmann.supplementary.caption,
                    color = stakeStateColor(row.state),
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
        Row(verticalAlignment = Alignment.CenterVertically) {
            InfoItem(
                icon = R.drawable.lock,
                label = stringResource(R.string.solana_rent_reserve),
                value = null,
            )
            UiSpacer(1f)
            Text(
                text = row.rentReserveDisplay,
                style = Theme.brockmann.body.m.medium,
                color = Theme.v2.colors.text.primary,
            )
        }

        // Only render the divider + action row when the account actually has actions, so states
        // with none (e.g. NotDelegated) don't leave an empty gap under the details.
        if (row.canWithdraw || row.canManage) {
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
                        background = Color.Transparent,
                        border = BorderStroke(1.dp, Theme.v2.colors.primary.accent4),
                        contentColor = Theme.v2.colors.text.primary,
                        onClick = onWithdraw,
                        modifier = Modifier.weight(1f),
                        iconCircleColor = Theme.v2.colors.text.tertiary,
                    )
                    // A cooled-down account can also finish a move — re-delegate it to a new
                    // validator.
                    ActionButton(
                        title = stringResource(R.string.solana_finish_move_cta),
                        icon = R.drawable.ic_arrow_bottom_top,
                        background = Theme.v2.colors.primary.accent3,
                        contentColor = Theme.v2.colors.text.primary,
                        onClick = onFinishMove,
                        modifier = Modifier.weight(1f),
                        iconCircleColor = Theme.v2.colors.primary.accent4,
                    )
                }
                if (row.canManage) {
                    // Unstake is hidden while Deactivating (deactivating an already-deactivating
                    // stake is a chain no-op); Move/Stake stay available through cooldown.
                    if (row.canUnstake) {
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
                    }
                    ActionButton(
                        title = stringResource(R.string.solana_staking_move_cta),
                        icon = R.drawable.ic_arrow_bottom_top,
                        background = Color.Transparent,
                        border = BorderStroke(1.dp, Theme.v2.colors.primary.accent4),
                        contentColor = Theme.v2.colors.text.primary,
                        onClick = onMove,
                        modifier = Modifier.weight(1f),
                        iconCircleColor = Theme.v2.colors.text.tertiary,
                    )
                    ActionButton(
                        title = stringResource(R.string.solana_staking_stake_cta),
                        icon = R.drawable.ic_circle_plus,
                        background = Theme.v2.colors.primary.accent3,
                        contentColor = Theme.v2.colors.text.primary,
                        onClick = onStake,
                        modifier = Modifier.weight(1f),
                        iconCircleColor = Theme.v2.colors.primary.accent4,
                    )
                }
            }
        }
    }
}

/**
 * Solana DeFi header box. The shared [com.vultisig.wallet.ui.screens.v2.defi.BalanceBanner] takes a
 * single full-bleed background image, and the repo has no wide Solana banner asset — so this local
 * banner keeps the same box style while placing the Solana coin logo, contained in a ring, on the
 * right (matching the iOS header) instead of overflowing the box.
 */
@Composable
private fun SolanaHeaderBanner(totalValue: String?, isLoading: Boolean, isBalanceVisible: Boolean) {
    Box(
        modifier =
            Modifier.fillMaxWidth()
                .height(118.dp)
                .clip(Theme.v2.radius.xl)
                .background(Theme.v2.colors.backgrounds.secondary)
                .border(
                    width = 1.dp,
                    color = Theme.v2.colors.border.light,
                    shape = Theme.v2.radius.xl,
                )
    ) {
        Box(
            modifier =
                Modifier.align(Alignment.CenterEnd)
                    .padding(end = 20.dp)
                    .size(76.dp)
                    .clip(CircleShape)
                    .border(1.dp, Theme.v2.colors.primary.accent4, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(R.drawable.solana),
                contentDescription = null,
                modifier = Modifier.size(40.dp),
            )
        }

        Column(modifier = Modifier.padding(start = 16.dp, top = 25.dp)) {
            Text(
                text = Chain.Solana.raw,
                color = Theme.v2.colors.text.primary,
                style = Theme.brockmann.body.l.medium,
            )
            UiSpacer(6.dp)
            if (isLoading) {
                UiPlaceholderLoader(modifier = Modifier.size(width = 150.dp, height = 32.dp))
            } else {
                Text(
                    text =
                        if (isBalanceVisible) totalValue ?: FIAT_VALUE_UNAVAILABLE
                        else HIDE_BALANCE_CHARS,
                    color = Theme.v2.colors.text.primary,
                    style = Theme.satoshi.price.title1,
                )
            }
        }
    }
}

/**
 * Maps a stake account's lifecycle to its label color: green for a healthy Active stake, amber
 * while a stake is transitioning (Activating/Deactivating), and muted for stakes that carry no
 * active delegation (Inactive/NotDelegated).
 */
@Composable
private fun stakeStateColor(state: SolanaStakeState): Color =
    when (state) {
        SolanaStakeState.Active -> Theme.v2.colors.alerts.success
        SolanaStakeState.Activating,
        SolanaStakeState.Deactivating -> Theme.v2.colors.alerts.warning
        SolanaStakeState.Inactive,
        SolanaStakeState.NotDelegated -> Theme.v2.colors.text.tertiary
    }

package com.vultisig.wallet.ui.screens.v2.defi.ton

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.vultisig.wallet.R
import com.vultisig.wallet.data.models.VaultId
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.buttons.VsButton
import com.vultisig.wallet.ui.components.buttons.VsButtonState
import com.vultisig.wallet.ui.components.buttons.VsButtonVariant
import com.vultisig.wallet.ui.components.library.UiPlaceholderLoader
import com.vultisig.wallet.ui.components.v2.tab.VsTab
import com.vultisig.wallet.ui.components.v2.tab.VsTabGroup
import com.vultisig.wallet.ui.models.defi.TON_KEY
import com.vultisig.wallet.ui.models.defi.TonDeFiPositionsViewModel
import com.vultisig.wallet.ui.models.defi.TonDeFiUiState
import com.vultisig.wallet.ui.models.defi.TonStakingUiModel
import com.vultisig.wallet.ui.screens.RegisterChainDashboardTopBarAction
import com.vultisig.wallet.ui.screens.v2.defi.DeFiTab
import com.vultisig.wallet.ui.screens.v2.defi.NoPositionsContainer
import com.vultisig.wallet.ui.screens.v2.defi.PositionsSelectionDialog
import com.vultisig.wallet.ui.theme.Theme
import com.vultisig.wallet.ui.utils.asString
import kotlinx.coroutines.delay

private val TON_DEFI_TABS = listOf(DeFiTab.STAKED)
private val HIDE_BALANCE_CHARS = "• ".repeat(8).trim()

private val TonBlue = Color(0xFF0098EA)
private val TonBannerGradientTop = TonBlue.copy(alpha = 0.09f)
private val TonBannerGradientBottom = TonBlue.copy(alpha = 0f)
private val TonBannerBorder = TonBlue.copy(alpha = 0.17f)

private data class CountdownParts(val days: Long, val hours: Long, val minutes: Long)

/** Returns the days/hours/minutes remaining until [expiryEpochMs], or null if already elapsed. */
private fun countdownParts(expiryEpochMs: Long, nowMs: Long): CountdownParts? {
    if (expiryEpochMs <= nowMs) return null
    val remaining = expiryEpochMs - nowMs
    return CountdownParts(
        days = remaining / (1_000L * 60 * 60 * 24),
        hours = (remaining % (1_000L * 60 * 60 * 24)) / (1_000L * 60 * 60),
        minutes = (remaining % (1_000L * 60 * 60)) / (1_000L * 60),
    )
}

/** Entry point for the TON DeFi positions screen; wires ViewModel state and pull-to-refresh. */
@Composable
internal fun TonDeFiPositionsScreen(
    vaultId: VaultId,
    viewModel: TonDeFiPositionsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    var isRefreshing by remember { mutableStateOf(false) }

    LaunchedEffect(state is TonDeFiUiState.Loading) {
        if (isRefreshing && state !is TonDeFiUiState.Loading) {
            isRefreshing = false
        }
    }

    LifecycleResumeEffect(vaultId) {
        viewModel.setData(vaultId)
        onPauseOrDispose {}
    }

    RegisterChainDashboardTopBarAction(
        icon = R.drawable.ic_shapes_plus_x_square_circle,
        onClick = { viewModel.setPositionSelectionDialogVisibility(true) },
    )

    TonDeFiPositionsScreenContent(
        state = state,
        isRefreshing = isRefreshing,
        onRefresh = {
            isRefreshing = true
            viewModel.refresh()
        },
        onTabSelected = viewModel::onTabSelected,
        onCancelEditPositionClick = { viewModel.setPositionSelectionDialogVisibility(false) },
        onDonePositionClick = viewModel::onPositionSelectionDone,
        onPositionSelectionChange = viewModel::onPositionSelectionChange,
        onClickStake = viewModel::onStake,
        onClickUnstake = viewModel::onUnstake,
    )
}

/** Stateless content for the TON DeFi positions screen with pull-to-refresh support. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TonDeFiPositionsScreenContent(
    state: TonDeFiUiState,
    isRefreshing: Boolean = false,
    onRefresh: () -> Unit = {},
    onTabSelected: (DeFiTab) -> Unit = {},
    onCancelEditPositionClick: () -> Unit = {},
    onDonePositionClick: () -> Unit = {},
    onPositionSelectionChange: (String, Boolean) -> Unit = { _, _ -> },
    onClickStake: () -> Unit = {},
    onClickUnstake: () -> Unit = {},
) {
    PullToRefreshBox(isRefreshing = isRefreshing, onRefresh = onRefresh) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.fillMaxSize().background(Theme.v2.colors.backgrounds.primary)
            ) {
                Box(modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp)) {
                    when (state) {
                        is TonDeFiUiState.Loading ->
                            TonDeFiBanner(
                                isLoading = true,
                                totalValue = "",
                                isBalanceVisible = true,
                            )
                        is TonDeFiUiState.Error ->
                            TonDeFiBanner(
                                isLoading = false,
                                totalValue = "",
                                isBalanceVisible = true,
                            )
                        is TonDeFiUiState.Success ->
                            TonDeFiBanner(
                                isLoading = false,
                                totalValue = state.tonData.totalAmountPrice,
                                isBalanceVisible = state.isBalanceVisible,
                            )
                    }
                }

                if (state is TonDeFiUiState.Success || state is TonDeFiUiState.Loading) {
                    val isLoading = state is TonDeFiUiState.Loading
                    Box(
                        modifier =
                            Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp)
                    ) {
                        VsTabGroup(index = 0) {
                            TON_DEFI_TABS.forEach { tab ->
                                tab {
                                    VsTab(
                                        label = stringResource(tab.displayNameRes),
                                        isEnabled = !isLoading,
                                        onClick = { onTabSelected(tab) },
                                    )
                                }
                            }
                        }
                    }
                }

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    when (state) {
                        is TonDeFiUiState.Loading -> {
                            item {
                                TonStakingPositionCard(
                                    data = TonStakingUiModel(hasPosition = true),
                                    isBalanceVisible = false,
                                    isLoading = true,
                                    onClickStake = {},
                                    onClickUnstake = {},
                                )
                            }
                        }
                        is TonDeFiUiState.Error -> {
                            item {
                                Text(
                                    text = state.error.asString(),
                                    style = Theme.brockmann.body.m.medium,
                                    color = Theme.v2.colors.alerts.error,
                                )
                            }
                        }
                        is TonDeFiUiState.Success -> {
                            val tonData = state.tonData
                            val isTonSelected = state.selectedPositions.contains(TON_KEY)
                            if (!isTonSelected) {
                                item { NoPositionsContainer() }
                            } else {
                                // Always render the position card (zeroed when there's no
                                // position),
                                // with Unstake disabled until there is one — mirrors iOS/macOS.
                                item {
                                    TonStakingPositionCard(
                                        data = tonData,
                                        isBalanceVisible = state.isBalanceVisible,
                                        onClickStake = onClickStake,
                                        onClickUnstake = onClickUnstake,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (state is TonDeFiUiState.Success && state.showPositionSelectionDialog) {
                val searchTextFieldState = remember { TextFieldState() }
                PositionsSelectionDialog(
                    stakePositions = state.stakePositionsDialog,
                    selectedPositions = state.tempSelectedPositions,
                    searchTextFieldState = searchTextFieldState,
                    onPositionSelectionChange = onPositionSelectionChange,
                    onDoneClick = onDonePositionClick,
                    onCancelClick = onCancelEditPositionClick,
                )
            }
        }
    }
}

/** Banner showing the staked-total fiat for the TON DeFi position. */
@Composable
private fun TonDeFiBanner(isLoading: Boolean, totalValue: String, isBalanceVisible: Boolean) {
    Row(
        modifier =
            Modifier.fillMaxWidth()
                .height(118.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(
                    Brush.verticalGradient(
                        colors = listOf(TonBannerGradientTop, TonBannerGradientBottom)
                    )
                )
                .border(1.dp, TonBannerBorder, RoundedCornerShape(16.dp))
    ) {
        Column(
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.Center,
            modifier =
                Modifier.fillMaxHeight()
                    .width(220.dp)
                    .padding(start = 16.dp, top = 16.dp, bottom = 16.dp),
        ) {
            Text(
                text = stringResource(R.string.ton),
                style = Theme.brockmann.body.l.medium,
                color = Theme.v2.colors.text.primary,
            )
            UiSpacer(6.dp)

            if (isLoading) {
                UiPlaceholderLoader(modifier = Modifier.size(width = 150.dp, height = 32.dp))
            } else {
                Text(
                    text = if (isBalanceVisible) totalValue else HIDE_BALANCE_CHARS,
                    style = Theme.satoshi.price.title1,
                    color = Theme.v2.colors.text.primary,
                )
            }
        }
    }
}

/** Card showing the active nominator-pool position with stake/unstake actions and unlock state. */
@Composable
private fun TonStakingPositionCard(
    data: TonStakingUiModel,
    isBalanceVisible: Boolean,
    isLoading: Boolean = false,
    onClickStake: () -> Unit,
    onClickUnstake: () -> Unit,
) {
    Column(
        modifier =
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Theme.v2.colors.backgrounds.secondary)
                .border(1.dp, Theme.v2.colors.border.light, RoundedCornerShape(16.dp))
                .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text =
                    data.poolName.ifBlank {
                        stringResource(R.string.ton_defi_staked_amount, data.ticker)
                    },
                style = Theme.brockmann.body.s.medium,
                color = Theme.v2.colors.text.tertiary,
            )
            if (isLoading) {
                UiPlaceholderLoader(modifier = Modifier.width(140.dp).height(28.dp))
            } else {
                Text(
                    text = if (isBalanceVisible) data.stakedDisplay else HIDE_BALANCE_CHARS,
                    style = Theme.brockmann.headings.title1,
                    color = Theme.v2.colors.text.primary,
                )
                if (data.stakedFiatDisplay.isNotEmpty()) {
                    Text(
                        text = if (isBalanceVisible) data.stakedFiatDisplay else HIDE_BALANCE_CHARS,
                        style = Theme.brockmann.body.s.medium,
                        color = Theme.v2.colors.text.tertiary,
                    )
                }
            }
        }

        if (!isLoading && data.apy != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.apy),
                    style = Theme.brockmann.body.s.medium,
                    color = Theme.v2.colors.text.tertiary,
                )
                Text(
                    text = data.apy,
                    style = Theme.brockmann.body.m.medium,
                    color = Theme.v2.colors.alerts.success,
                )
            }
        }

        if (!isLoading && data.isActionLocked) {
            TonUnlockNotice(data = data)
        }

        if (!isLoading) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                VsButton(
                    label = stringResource(R.string.defi_action_stake),
                    variant = VsButtonVariant.Secondary,
                    state =
                        if (data.isActionLocked) VsButtonState.Disabled else VsButtonState.Enabled,
                    onClick = onClickStake,
                    modifier = Modifier.weight(1f),
                )
                VsButton(
                    label = stringResource(R.string.defi_action_unstake),
                    variant = VsButtonVariant.Secondary,
                    // Unstake needs an existing position; disabled at 0 staked (and while locked).
                    state =
                        if (data.hasPosition && !data.isActionLocked) VsButtonState.Enabled
                        else VsButtonState.Disabled,
                    onClick = onClickUnstake,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/**
 * Pending-withdrawal notice with a live unlock countdown, shown while a withdrawal is in flight.
 */
@Composable
private fun TonUnlockNotice(data: TonStakingUiModel) {
    val unlockMs = data.unlockEpochMs
    val nowMs by
        produceState(initialValue = System.currentTimeMillis(), key1 = unlockMs) {
            val target = unlockMs ?: return@produceState
            while (value < target) {
                val delta = target - value
                val interval = if (delta <= 60_000L) 1_000L else 60_000L
                delay(interval)
                value = System.currentTimeMillis()
            }
        }
    val countdown = unlockMs?.let { countdownParts(it, nowMs) }
    val unlockText =
        when {
            countdown == null -> stringResource(R.string.ton_defi_unlock_ready)
            countdown.days > 0 ->
                stringResource(R.string.ton_defi_unlocks_in_days, countdown.days, countdown.hours)
            else ->
                stringResource(
                    R.string.ton_defi_unlocks_in_hours,
                    countdown.hours,
                    countdown.minutes,
                )
        }

    Row(
        modifier =
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(Theme.v2.colors.backgrounds.surface2)
                .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = stringResource(R.string.ton_defi_pending_withdrawal),
                style = Theme.brockmann.body.s.medium,
                color = Theme.v2.colors.text.secondary,
            )
            data.pendingWithdrawDisplay?.let { pending ->
                Text(
                    text = pending,
                    style = Theme.brockmann.body.s.medium,
                    color = Theme.v2.colors.text.primary,
                )
            }
        }
        Text(
            text = unlockText,
            style = Theme.brockmann.body.s.medium,
            color = Theme.v2.colors.text.primary,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun TonDeFiPositionsScreenLoadingPreview() {
    TonDeFiPositionsScreenContent(state = TonDeFiUiState.Loading)
}

@Preview(showBackground = true)
@Composable
private fun TonDeFiPositionsScreenPositionPreview() {
    TonDeFiPositionsScreenContent(
        state =
            TonDeFiUiState.Success(
                tonData =
                    TonStakingUiModel(
                        totalAmountPrice = "$152.40",
                        poolName = "Whales Nominators #1",
                        stakedDisplay = "50.8 GRAM",
                        stakedFiatDisplay = "$152.40",
                        apy = "13.27%",
                        hasPosition = true,
                    )
            )
    )
}

@Preview(showBackground = true)
@Composable
private fun TonDeFiPositionsScreenLockedPreview() {
    TonDeFiPositionsScreenContent(
        state =
            TonDeFiUiState.Success(
                tonData =
                    TonStakingUiModel(
                        totalAmountPrice = "$152.40",
                        poolName = "Whales Nominators #1",
                        stakedDisplay = "50.8 GRAM",
                        stakedFiatDisplay = "$152.40",
                        apy = "13.27%",
                        hasPosition = true,
                        isActionLocked = true,
                        pendingWithdrawDisplay = "50.8 GRAM",
                        unlockEpochMs = System.currentTimeMillis() + 18 * 60 * 60 * 1_000L,
                    )
            )
    )
}

@Preview(showBackground = true)
@Composable
private fun TonDeFiPositionsScreenEmptyPreview() {
    TonDeFiPositionsScreenContent(
        state = TonDeFiUiState.Success(tonData = TonStakingUiModel(hasPosition = false))
    )
}

package com.vultisig.wallet.ui.screens.v2.defi.tron

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.vultisig.wallet.R
import com.vultisig.wallet.data.api.models.ResourceUsage
import com.vultisig.wallet.data.blockchain.tron.TronResourceType
import com.vultisig.wallet.data.models.VaultId
import com.vultisig.wallet.ui.components.UiIcon
import com.vultisig.wallet.ui.components.clickOnce
import com.vultisig.wallet.ui.components.v2.containers.ContainerType
import com.vultisig.wallet.ui.components.v2.containers.CornerType
import com.vultisig.wallet.ui.components.v2.containers.V2Container
import com.vultisig.wallet.ui.components.v2.tab.VsTab
import com.vultisig.wallet.ui.components.v2.tab.VsTabGroup
import com.vultisig.wallet.ui.models.defi.TronAction
import com.vultisig.wallet.ui.models.defi.TronDeFiPositionsViewModel
import com.vultisig.wallet.ui.models.defi.TronDeFiUiState
import com.vultisig.wallet.ui.models.defi.TronPendingWithdrawalUiModel
import com.vultisig.wallet.ui.models.defi.TronStakingUiModel
import com.vultisig.wallet.ui.screens.ResourceTwoCardsRow
import com.vultisig.wallet.ui.screens.v2.defi.DeFiTab
import com.vultisig.wallet.ui.screens.v2.defi.NoPositionsContainer
import com.vultisig.wallet.ui.screens.v2.defi.PositionsSelectionDialog
import com.vultisig.wallet.ui.theme.Theme
import com.vultisig.wallet.ui.utils.asString
import kotlinx.coroutines.delay

private val TRON_DEFI_TABS = listOf(DeFiTab.STAKED)
private const val TWO_DAYS_MS = 2 * 24 * 60 * 60 * 1_000L

private data class CountdownParts(val days: Long, val hours: Long, val minutes: Long)

/** Returns the days/hours/minutes remaining until [expiryEpochMs], or null if already expired. */
private fun countdownParts(expiryEpochMs: Long, nowMs: Long): CountdownParts? {
    if (expiryEpochMs <= nowMs) return null
    val remaining = expiryEpochMs - nowMs
    return CountdownParts(
        days = remaining / (1_000L * 60 * 60 * 24),
        hours = (remaining % (1_000L * 60 * 60 * 24)) / (1_000L * 60 * 60),
        minutes = (remaining % (1_000L * 60 * 60)) / (1_000L * 60),
    )
}

/** Entry point for the TRON DeFi positions screen; wires ViewModel state and pull-to-refresh. */
@Composable
internal fun TronDeFiPositionsScreen(
    vaultId: VaultId,
    viewModel: TronDeFiPositionsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    var isRefreshing by remember { mutableStateOf(false) }

    LaunchedEffect(state is TronDeFiUiState.Loading) {
        if (isRefreshing && state !is TronDeFiUiState.Loading) {
            isRefreshing = false
        }
    }

    LifecycleResumeEffect(vaultId) {
        viewModel.setData(vaultId)
        onPauseOrDispose {}
    }

    TronDeFiPositionsScreenContent(
        state = state,
        isRefreshing = isRefreshing,
        onRefresh = {
            isRefreshing = true
            viewModel.refresh()
        },
        onTabSelected = viewModel::onTabSelected,
        onEditPositionClick = { viewModel.setPositionSelectionDialogVisibility(true) },
        onCancelEditPositionClick = { viewModel.setPositionSelectionDialogVisibility(false) },
        onDonePositionClick = viewModel::onPositionSelectionDone,
        onPositionSelectionChange = viewModel::onPositionSelectionChange,
        onClickFreeze = { viewModel.onTronAction(TronAction.FREEZE) },
        onClickUnfreeze = { viewModel.onTronAction(TronAction.UNFREEZE) },
    )
}

/** Stateless content for the TRON DeFi positions screen with pull-to-refresh support. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TronDeFiPositionsScreenContent(
    state: TronDeFiUiState,
    isRefreshing: Boolean = false,
    onRefresh: () -> Unit = {},
    onTabSelected: (DeFiTab) -> Unit = {},
    onEditPositionClick: () -> Unit = {},
    onCancelEditPositionClick: () -> Unit = {},
    onDonePositionClick: () -> Unit = {},
    onPositionSelectionChange: (String, Boolean) -> Unit = { _, _ -> },
    onClickFreeze: () -> Unit = {},
    onClickUnfreeze: () -> Unit = {},
) {
    PullToRefreshBox(isRefreshing = isRefreshing, onRefresh = onRefresh) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.fillMaxSize().background(Theme.v2.colors.backgrounds.primary)
            ) {
                Box(modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp)) {
                    when (state) {
                        TronDeFiUiState.Loading ->
                            TronDeFiBanner(
                                isLoading = true,
                                totalValue = "",
                                isBalanceVisible = true,
                            )
                        is TronDeFiUiState.Error ->
                            TronDeFiBanner(
                                isLoading = false,
                                totalValue = "",
                                isBalanceVisible = true,
                            )
                        is TronDeFiUiState.Success ->
                            TronDeFiBanner(
                                isLoading = false,
                                totalValue = state.tronData.totalAmountPrice,
                                isBalanceVisible = state.isBalanceVisible,
                            )
                    }
                }

                if (state is TronDeFiUiState.Success || state is TronDeFiUiState.Loading) {
                    val isLoading = state is TronDeFiUiState.Loading
                    Row(
                        modifier =
                            Modifier.fillMaxWidth()
                                .padding(start = 16.dp, end = 16.dp, top = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        VsTabGroup(
                            index =
                                if (state is TronDeFiUiState.Success)
                                    TRON_DEFI_TABS.indexOfFirst { it == state.selectedTab }
                                        .coerceAtLeast(0)
                                else 0
                        ) {
                            TRON_DEFI_TABS.forEach { tab ->
                                tab {
                                    VsTab(
                                        label = stringResource(tab.displayNameRes),
                                        onClick = { if (!isLoading) onTabSelected(tab) },
                                    )
                                }
                            }
                        }

                        V2Container(
                            type = ContainerType.SECONDARY,
                            cornerType = CornerType.Circular,
                            modifier =
                                if (isLoading) Modifier
                                else Modifier.clickOnce(onClick = onEditPositionClick),
                        ) {
                            UiIcon(
                                drawableResId = R.drawable.edit_chain,
                                size = 16.dp,
                                modifier = Modifier.padding(all = 12.dp),
                                tint =
                                    if (isLoading) Theme.v2.colors.text.tertiary
                                    else Theme.v2.colors.primary.accent4,
                            )
                        }
                    }
                }

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    when (state) {
                        TronDeFiUiState.Loading -> {
                            item {
                                ResourceTwoCardsRow(
                                    resourceUsage =
                                        ResourceUsage(
                                            availableBandwidth = 0L,
                                            totalBandwidth = 0L,
                                            availableEnergy = 0L,
                                            totalEnergy = 0L,
                                        )
                                )
                            }
                            item { NoPositionsContainer() }
                        }
                        is TronDeFiUiState.Error -> {
                            item {
                                Text(
                                    text = state.error.asString(),
                                    style = Theme.brockmann.body.m.medium,
                                    color = Theme.v2.colors.alerts.error,
                                )
                            }
                        }
                        is TronDeFiUiState.Success -> {
                            val tronData = state.tronData

                            item {
                                ResourceTwoCardsRow(
                                    resourceUsage =
                                        ResourceUsage(
                                            availableBandwidth = tronData.availableBandwidth,
                                            totalBandwidth = tronData.totalBandwidth,
                                            availableEnergy = tronData.availableEnergy,
                                            totalEnergy = tronData.totalEnergy,
                                        )
                                )
                            }

                            val isTronSelected = state.selectedPositions.contains("TRON")
                            val pendingWithdrawals = tronData.pendingWithdrawals
                            if (isTronSelected) {
                                item {
                                    TronFreezePositionCard(
                                        frozenTotalPrice = tronData.frozenTotalPrice,
                                        frozenTotalTrx = tronData.frozenTotalTrx,
                                        isBalanceVisible = state.isBalanceVisible,
                                        isUnfreezeEnabled = tronData.hasFrozenBalance,
                                        onClickFreeze = onClickFreeze,
                                        onClickUnfreeze = onClickUnfreeze,
                                    )
                                }
                            } else if (pendingWithdrawals.isEmpty()) {
                                item { NoPositionsContainer() }
                            }

                            if (pendingWithdrawals.isNotEmpty()) {
                                TronPendingWithdrawalsCard(
                                    withdrawals = pendingWithdrawals,
                                    isBalanceVisible = state.isBalanceVisible,
                                )
                            }
                        }
                    }
                }
            }

            if (state is TronDeFiUiState.Success && state.showPositionSelectionDialog) {
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

/** Lazy list section for pending TRON withdrawals: header text followed by individual rows. */
private fun LazyListScope.TronPendingWithdrawalsCard(
    withdrawals: List<TronPendingWithdrawalUiModel>,
    isBalanceVisible: Boolean,
) {
    item(key = "tron-pending-withdrawals-header") {
        Text(
            text = stringResource(R.string.tron_defi_pending_withdrawals),
            style = Theme.brockmann.body.l.medium,
            color = Theme.v2.colors.text.primary,
        )
    }
    items(items = withdrawals, key = { it.expiryEpochMs }) { withdrawal ->
        TronPendingWithdrawalRow(withdrawal = withdrawal, isBalanceVisible = isBalanceVisible)
    }
}

/** Row displaying a single pending TRX withdrawal with a live countdown or claimable badge. */
@Composable
private fun TronPendingWithdrawalRow(
    withdrawal: TronPendingWithdrawalUiModel,
    isBalanceVisible: Boolean,
) {
    val expiryMs = withdrawal.expiryEpochMs
    val nowMs by
        produceState(initialValue = System.currentTimeMillis(), key1 = expiryMs) {
            while (value < expiryMs) {
                val delta = expiryMs - value
                val interval = if (delta <= 60_000L) 1_000L else 60_000L
                delay(interval)
                value = System.currentTimeMillis()
            }
        }
    val countdown = countdownParts(expiryMs, nowMs)
    val isClaimable = countdown == null
    val timeRemainingText =
        when {
            countdown == null -> stringResource(R.string.tron_defi_ready_to_claim)
            countdown.days > 0 ->
                stringResource(R.string.tron_defi_countdown_days, countdown.days, countdown.hours)
            else ->
                stringResource(
                    R.string.tron_defi_countdown_hours,
                    countdown.hours,
                    countdown.minutes,
                )
        }

    Row(
        modifier =
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Theme.v2.colors.backgrounds.secondary)
                .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = if (isBalanceVisible) "${withdrawal.amountTrx} TRX" else HIDE_BALANCE_CHARS,
                style = Theme.brockmann.body.m.medium,
                color = Theme.v2.colors.text.primary,
            )
            if (withdrawal.resourceType != null || !isClaimable) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    withdrawal.resourceType?.let { TronResourceTypeBadge(it) }
                    if (!isClaimable) {
                        Text(
                            text = timeRemainingText,
                            style = Theme.brockmann.body.s.medium,
                            color = Theme.v2.colors.text.secondary,
                        )
                    }
                }
            }
        }

        if (isClaimable) {
            Box(
                modifier =
                    Modifier.clip(RoundedCornerShape(8.dp))
                        .background(Theme.v2.colors.alerts.success.copy(alpha = 0.12f))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    text = timeRemainingText,
                    style = Theme.brockmann.body.s.medium,
                    color = Theme.v2.colors.alerts.success,
                )
            }
        }
    }
}

/** Pill badge showing the TRX resource type (bandwidth or energy) with icon and label. */
@Composable
private fun TronResourceTypeBadge(resourceType: TronResourceType) {
    val labelRes =
        when (resourceType) {
            TronResourceType.BANDWIDTH -> R.string.tron_resource_bandwidth
            TronResourceType.ENERGY -> R.string.tron_resource_energy
        }
    val iconRes =
        when (resourceType) {
            TronResourceType.BANDWIDTH -> R.drawable.bandwidth
            TronResourceType.ENERGY -> R.drawable.energy
        }
    val iconTint =
        when (resourceType) {
            TronResourceType.BANDWIDTH -> Theme.v2.colors.alerts.success
            TronResourceType.ENERGY -> Theme.v2.colors.alerts.warning
        }
    Row(
        modifier =
            Modifier.clip(RoundedCornerShape(6.dp))
                .background(Theme.v2.colors.backgrounds.surface2)
                .padding(horizontal = 8.dp, vertical = 3.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        UiIcon(drawableResId = iconRes, size = 12.dp, tint = iconTint)
        Text(
            text = stringResource(labelRes),
            style = Theme.brockmann.body.s.medium,
            color = Theme.v2.colors.text.secondary,
        )
    }
}

/** Preview for [TronDeFiPositionsScreenContent] in loading state. */
@Preview(showBackground = true)
@Composable
private fun TronDeFiPositionsScreenLoadingPreview() {
    TronDeFiPositionsScreenContent(state = TronDeFiUiState.Loading)
}

/** Preview for [TronDeFiPositionsScreenContent] in error state. */
@Preview(showBackground = true)
@Composable
private fun TronDeFiPositionsScreenErrorPreview() {
    TronDeFiPositionsScreenContent(
        state =
            TronDeFiUiState.Error(
                com.vultisig.wallet.ui.utils.UiText.DynamicString("TRX coin not found in vault")
            )
    )
}

/** Preview for [TronDeFiPositionsScreenContent] with no positions. */
@Preview(showBackground = true)
@Composable
private fun TronDeFiPositionsScreenNoPositionsPreview() {
    TronDeFiPositionsScreenContent(
        state =
            TronDeFiUiState.Success(
                tronData =
                    TronStakingUiModel(
                        totalAmountPrice = "$1240.05",
                        availableBandwidth = 1500L,
                        totalBandwidth = 2000L,
                        availableEnergy = 1L,
                        totalEnergy = 2L,
                    )
            )
    )
}

/** Preview for [TronDeFiPositionsScreenContent] with sample freeze and withdrawal data. */
@Preview(showBackground = true)
@Composable
private fun TronDeFiPositionsScreenPreview() {
    TronDeFiPositionsScreenContent(
        state =
            TronDeFiUiState.Success(
                tronData =
                    TronStakingUiModel(
                        totalAmountPrice = "$1240.05",
                        frozenTotalPrice = "$4,800",
                        frozenTotalTrx = "800",
                        hasFrozenBalance = true,
                        availableBandwidth = 15000L,
                        totalBandwidth = 20000L,
                        availableEnergy = 50000L,
                        totalEnergy = 100000L,
                        pendingWithdrawals =
                            listOf(
                                TronPendingWithdrawalUiModel(
                                    amountTrx = "50.000000",
                                    expiryEpochMs = System.currentTimeMillis() - 1_000L,
                                    resourceType = TronResourceType.BANDWIDTH,
                                ),
                                TronPendingWithdrawalUiModel(
                                    amountTrx = "30.000000",
                                    expiryEpochMs = System.currentTimeMillis() + TWO_DAYS_MS,
                                    resourceType = TronResourceType.ENERGY,
                                ),
                            ),
                    )
            )
    )
}

package com.vultisig.wallet.ui.screens.v2.defi.tron

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.vultisig.wallet.R
import com.vultisig.wallet.data.api.models.ResourceUsage
import com.vultisig.wallet.data.blockchain.tron.TronResourceType
import com.vultisig.wallet.data.models.VaultId
import com.vultisig.wallet.ui.components.UiAlertDialog
import com.vultisig.wallet.ui.components.v2.tab.VsTab
import com.vultisig.wallet.ui.components.v2.tab.VsTabGroup
import com.vultisig.wallet.ui.models.defi.TronAction
import com.vultisig.wallet.ui.models.defi.TronDeFiPositionsViewModel
import com.vultisig.wallet.ui.models.defi.TronDeFiUiState
import com.vultisig.wallet.ui.models.defi.TronPendingWithdrawalUiModel
import com.vultisig.wallet.ui.models.defi.TronStakingUiModel
import com.vultisig.wallet.ui.screens.ResourceTwoCardsRow
import com.vultisig.wallet.ui.screens.v2.defi.DeFiTab
import com.vultisig.wallet.ui.screens.v2.defi.ManagePositionsButton
import com.vultisig.wallet.ui.screens.v2.defi.NoPositionsContainer
import com.vultisig.wallet.ui.screens.v2.defi.PositionsSelectionDialog
import com.vultisig.wallet.ui.theme.Theme
import com.vultisig.wallet.ui.utils.asString

private val TRON_DEFI_TABS = listOf(DeFiTab.STAKED)
private const val TWO_DAYS_MS = 2 * 24 * 60 * 60 * 1_000L

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
        onClaimWithdrawal = viewModel::onClaimExpiredWithdrawals,
        onDismissClaimError = viewModel::onDismissClaimError,
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
    onClaimWithdrawal: () -> Unit = {},
    onDismissClaimError: () -> Unit = {},
) {
    PullToRefreshBox(isRefreshing = isRefreshing, onRefresh = onRefresh) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Banner and tab row are leading list items so the whole screen scrolls as one surface
            // instead of pinning the header above the list, mirroring iOS (#4761).
            LazyColumn(
                modifier = Modifier.fillMaxSize().background(Theme.v2.colors.backgrounds.primary),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item {
                    when (state) {
                        is TronDeFiUiState.Loading ->
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
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            VsTabGroup(index = 0) {
                                TRON_DEFI_TABS.forEach { tab ->
                                    tab {
                                        VsTab(
                                            label = stringResource(tab.displayNameRes),
                                            isEnabled = !isLoading,
                                            onClick = { onTabSelected(tab) },
                                        )
                                    }
                                }
                            }

                            ManagePositionsButton(
                                onClick = onEditPositionClick,
                                isEnabled = !isLoading,
                            )
                        }
                    }
                }

                when (state) {
                    is TronDeFiUiState.Loading -> {
                        item {
                            ResourceTwoCardsRow(
                                resourceUsage =
                                    ResourceUsage(
                                        availableBandwidth = 0L,
                                        totalBandwidth = 0L,
                                        availableEnergy = 0L,
                                        totalEnergy = 0L,
                                    ),
                                isLoading = true,
                            )
                        }
                        item {
                            TronFreezePositionCard(
                                frozenTotalPrice = "",
                                frozenTotalTrx = "",
                                isBalanceVisible = false,
                                isLoading = true,
                                isUnfreezeEnabled = false,
                                onClickFreeze = {},
                                onClickUnfreeze = {},
                            )
                        }
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
                                    isFreezeEnabled = tronData.hasAvailableBalance,
                                    onClickFreeze = onClickFreeze,
                                    onClickUnfreeze = onClickUnfreeze,
                                )
                            }
                        } else if (pendingWithdrawals.isEmpty()) {
                            item { NoPositionsContainer() }
                        }

                        if (pendingWithdrawals.isNotEmpty()) {
                            item {
                                TronPendingWithdrawalsCard(
                                    withdrawals = pendingWithdrawals,
                                    totalTrx = tronData.pendingWithdrawalsTotalTrx,
                                    isBalanceVisible = state.isBalanceVisible,
                                    isClaiming = state.isClaimingWithdrawal,
                                    onClaim = onClaimWithdrawal,
                                )
                            }
                        }
                    }
                }
            }

            val claimError = (state as? TronDeFiUiState.Success)?.claimError
            if (claimError != null) {
                UiAlertDialog(
                    title = stringResource(R.string.dialog_default_error_title),
                    text = claimError.asString(),
                    onDismiss = onDismissClaimError,
                )
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
                                    amountTrx = "50",
                                    expiryEpochMs = System.currentTimeMillis() - 1_000L,
                                    resourceType = TronResourceType.BANDWIDTH,
                                    amountSun = 50_000_000L,
                                ),
                                TronPendingWithdrawalUiModel(
                                    amountTrx = "30",
                                    expiryEpochMs = System.currentTimeMillis() + TWO_DAYS_MS,
                                    resourceType = TronResourceType.ENERGY,
                                    amountSun = 30_000_000L,
                                ),
                            ),
                        pendingWithdrawalsTotalTrx = "80",
                    )
            )
    )
}

package com.vultisig.wallet.ui.screens.v2.defi.tron

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vultisig.wallet.R
import com.vultisig.wallet.data.api.models.ResourceUsage
import com.vultisig.wallet.data.models.VaultId
import com.vultisig.wallet.ui.components.UiIcon
import com.vultisig.wallet.ui.components.clickOnce
import com.vultisig.wallet.ui.components.v2.containers.ContainerType
import com.vultisig.wallet.ui.components.v2.containers.CornerType
import com.vultisig.wallet.ui.components.v2.containers.V2Container
import com.vultisig.wallet.ui.components.v2.tab.VsTab
import com.vultisig.wallet.ui.components.v2.tab.VsTabGroup
import com.vultisig.wallet.ui.models.defi.TronDeFiPositionsViewModel
import com.vultisig.wallet.ui.models.defi.TronDeFiUiState
import com.vultisig.wallet.ui.models.defi.TronPendingWithdrawalUiModel
import com.vultisig.wallet.ui.screens.ResourceTwoCardsRow
import com.vultisig.wallet.ui.screens.v2.defi.DeFiTab
import com.vultisig.wallet.ui.screens.v2.defi.NoPositionsContainer
import com.vultisig.wallet.ui.theme.Theme

private const val TRON_RESOURCE_BANDWIDTH = "BANDWIDTH"

private val TRON_DEFI_TABS = listOf(DeFiTab.STAKED)

@Composable
internal fun TronDeFiPositionsScreen(
    vaultId: VaultId,
    viewModel: TronDeFiPositionsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(vaultId) { viewModel.setData(vaultId) }

    TronDeFiPositionsScreenContent(
        state = state,
        onTabSelected = viewModel::onTabSelected,
        onEditPositionClick = { viewModel.setPositionSelectionDialogVisibility(true) },
    )
}

@Composable
private fun TronDeFiPositionsScreenContent(
    state: TronDeFiUiState,
    onTabSelected: (DeFiTab) -> Unit = {},
    onEditPositionClick: () -> Unit = {},
) {
    val hasNoFrozenPositions =
        !state.isLoading &&
            state.frozenBandwidthTrx.toBigDecimalOrNull()?.signum() == 0 &&
            state.frozenEnergyTrx.toBigDecimalOrNull()?.signum() == 0

    Column(
        modifier =
            Modifier.fillMaxSize()
                .background(Theme.v2.colors.backgrounds.primary)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        TronDeFiBanner(
            isLoading = state.isLoading,
            totalValue = state.totalAmountPrice,
            isBalanceVisible = state.isBalanceVisible,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            VsTabGroup(
                index = TRON_DEFI_TABS.indexOfFirst { it.displayNameRes == state.selectedTab }
            ) {
                TRON_DEFI_TABS.forEach { tab ->
                    tab {
                        VsTab(
                            label = stringResource(tab.displayNameRes),
                            onClick = { onTabSelected(tab) },
                        )
                    }
                }
            }

            V2Container(
                type = ContainerType.SECONDARY,
                cornerType = CornerType.Circular,
                modifier = Modifier.clickOnce(onClick = onEditPositionClick),
            ) {
                UiIcon(
                    drawableResId = R.drawable.edit_chain,
                    size = 16.dp,
                    modifier = Modifier.padding(all = 12.dp),
                    tint = Theme.v2.colors.primary.accent4,
                )
            }
        }

        ResourceTwoCardsRow(
            resourceUsage =
                ResourceUsage(
                    availableBandwidth = state.availableBandwidth,
                    totalBandwidth = state.totalBandwidth,
                    availableEnergy = state.availableEnergy,
                    totalEnergy = state.totalEnergy,
                )
        )

        if (hasNoFrozenPositions) {
            NoPositionsContainer()
        }

        if (state.pendingWithdrawals.isNotEmpty()) {
            TronPendingWithdrawalsCard(withdrawals = state.pendingWithdrawals)
        }
    }
}

@Composable
private fun TronPendingWithdrawalsCard(withdrawals: List<TronPendingWithdrawalUiModel>) {
    Column(
        modifier =
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Theme.v2.colors.backgrounds.secondary)
                .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(R.string.tron_defi_pending_withdrawals),
            style = Theme.brockmann.body.l.medium,
            color = Theme.v2.colors.text.primary,
        )

        withdrawals.forEach { withdrawal -> TronPendingWithdrawalRow(withdrawal = withdrawal) }
    }
}

@Composable
private fun TronPendingWithdrawalRow(withdrawal: TronPendingWithdrawalUiModel) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = "${withdrawal.amountTrx} TRX",
                style = Theme.brockmann.body.m.medium,
                color = Theme.v2.colors.text.primary,
            )
            Text(
                text = withdrawal.timeRemaining,
                style = Theme.brockmann.body.s.medium,
                color =
                    if (withdrawal.isClaimable) Theme.v2.colors.alerts.success
                    else Theme.v2.colors.text.secondary,
            )
        }

        if (withdrawal.isClaimable) {
            Box(
                modifier =
                    Modifier.clip(RoundedCornerShape(8.dp))
                        .background(Theme.v2.colors.alerts.success.copy(alpha = 0.12f))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    text = stringResource(R.string.tron_defi_ready_to_claim),
                    style = Theme.brockmann.body.s.medium,
                    color = Theme.v2.colors.alerts.success,
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun TronDeFiPositionsScreenNoPositionsPreview() {
    TronDeFiPositionsScreenContent(
        state =
            TronDeFiUiState(
                isLoading = false,
                availableBalanceTrx = "1240.50",
                totalAmountPrice = "$1240.05",
                frozenBandwidthTrx = "0.000000",
                frozenEnergyTrx = "0.000000",
                unfreezingTrx = "0.000000",
                availableBandwidth = 1500L,
                totalBandwidth = 2000L,
                availableEnergy = 1L,
                totalEnergy = 2L,
                bandwidthProgress = 0.75f,
                energyProgress = 0.5f,
            )
    )
}

@Preview(showBackground = true)
@Composable
private fun TronDeFiPositionsScreenPreview() {
    TronDeFiPositionsScreenContent(
        state =
            TronDeFiUiState(
                isLoading = false,
                availableBalanceTrx = "1240.50",
                totalAmountPrice = "$1240.05",
                frozenBandwidthTrx = "100.000000",
                frozenEnergyTrx = "200.000000",
                unfreezingTrx = "50.000000",
                availableBandwidth = 15000L,
                totalBandwidth = 20000L,
                availableEnergy = 50000L,
                totalEnergy = 100000L,
                bandwidthProgress = 0.75f,
                energyProgress = 0.5f,
                pendingWithdrawals =
                    listOf(
                        TronPendingWithdrawalUiModel(
                            amountTrx = "50.000000",
                            timeRemaining = "Ready to claim",
                            isClaimable = true,
                        ),
                        TronPendingWithdrawalUiModel(
                            amountTrx = "30.000000",
                            timeRemaining = "2 days, 5 hrs",
                            isClaimable = false,
                        ),
                    ),
            )
    )
}

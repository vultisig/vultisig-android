package com.vultisig.wallet.ui.screens.v2.defi.tron

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vultisig.wallet.R
import com.vultisig.wallet.data.models.VaultId
import com.vultisig.wallet.ui.components.UiIcon
import com.vultisig.wallet.ui.components.buttons.VsButton
import com.vultisig.wallet.ui.components.buttons.VsButtonSize
import com.vultisig.wallet.ui.components.buttons.VsButtonState
import com.vultisig.wallet.ui.components.buttons.VsButtonVariant
import com.vultisig.wallet.ui.components.library.UiPlaceholderLoader
import com.vultisig.wallet.ui.models.defi.TronDeFiPositionsViewModel
import com.vultisig.wallet.ui.models.defi.TronDeFiUiState
import com.vultisig.wallet.ui.models.defi.TronPendingWithdrawalUiModel
import com.vultisig.wallet.ui.screens.v2.defi.BalanceBanner
import com.vultisig.wallet.ui.theme.Theme

private const val TRON_RESOURCE_BANDWIDTH = "BANDWIDTH"
private const val TRON_RESOURCE_ENERGY = "ENERGY"

@Composable
internal fun TronDeFiPositionsScreen(
    vaultId: VaultId,
    viewModel: TronDeFiPositionsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(vaultId) { viewModel.setData(vaultId) }

    TronDeFiPositionsScreenContent(
        state = state,
        onClickFreezeBandwidth = { viewModel.onClickFreeze(TRON_RESOURCE_BANDWIDTH) },
        onClickFreezeEnergy = { viewModel.onClickFreeze(TRON_RESOURCE_ENERGY) },
        onClickUnfreezeBandwidth = { viewModel.onClickUnfreeze(TRON_RESOURCE_BANDWIDTH) },
        onClickUnfreezeEnergy = { viewModel.onClickUnfreeze(TRON_RESOURCE_ENERGY) },
    )
}

@Composable
private fun TronDeFiPositionsScreenContent(
    state: TronDeFiUiState,
    onClickFreezeBandwidth: () -> Unit = {},
    onClickFreezeEnergy: () -> Unit = {},
    onClickUnfreezeBandwidth: () -> Unit = {},
    onClickUnfreezeEnergy: () -> Unit = {},
) {
    Column(
        modifier =
            Modifier.fillMaxSize()
                .background(Theme.v2.colors.backgrounds.primary)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        BalanceBanner(
            title = stringResource(R.string.tron_defi_available_balance),
            isLoading = state.isLoading,
            totalValue = "${state.availableBalanceTrx} TRX",
            image = R.drawable.referral_data_banner,
            isBalanceVisible = state.isBalanceVisible,
        )

        TronFrozenCard(
            isLoading = state.isLoading,
            frozenBandwidthTrx = state.frozenBandwidthTrx,
            frozenEnergyTrx = state.frozenEnergyTrx,
            unfreezingTrx = state.unfreezingTrx,
            onClickFreezeBandwidth = onClickFreezeBandwidth,
            onClickFreezeEnergy = onClickFreezeEnergy,
            onClickUnfreezeBandwidth = onClickUnfreezeBandwidth,
            onClickUnfreezeEnergy = onClickUnfreezeEnergy,
        )

        TronResourcesCard(
            isLoading = state.isLoading,
            availableBandwidth = state.availableBandwidth,
            totalBandwidth = state.totalBandwidth,
            availableEnergy = state.availableEnergy,
            totalEnergy = state.totalEnergy,
            bandwidthProgress = state.bandwidthProgress,
            energyProgress = state.energyProgress,
        )

        if (state.pendingWithdrawals.isNotEmpty()) {
            TronPendingWithdrawalsCard(withdrawals = state.pendingWithdrawals)
        }
    }
}

@Composable
private fun TronFrozenCard(
    isLoading: Boolean,
    frozenBandwidthTrx: String,
    frozenEnergyTrx: String,
    unfreezingTrx: String,
    onClickFreezeBandwidth: () -> Unit,
    onClickFreezeEnergy: () -> Unit,
    onClickUnfreezeBandwidth: () -> Unit,
    onClickUnfreezeEnergy: () -> Unit,
) {
    Column(
        modifier =
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Theme.v2.colors.backgrounds.secondary)
                .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(R.string.tron_defi_staking),
            style = Theme.brockmann.body.l.medium,
            color = Theme.v2.colors.text.primary,
        )

        TronFrozenRow(
            label = stringResource(R.string.tron_defi_bandwidth),
            isLoading = isLoading,
            amountTrx = frozenBandwidthTrx,
            onClickFreeze = onClickFreezeBandwidth,
            onClickUnfreeze = onClickUnfreezeBandwidth,
        )

        TronFrozenRow(
            label = stringResource(R.string.tron_defi_energy),
            isLoading = isLoading,
            amountTrx = frozenEnergyTrx,
            onClickFreeze = onClickFreezeEnergy,
            onClickUnfreeze = onClickUnfreezeEnergy,
        )

        if (!isLoading && unfreezingTrx != "0.000000" && unfreezingTrx != "0") {
            TronInfoRow(
                label = stringResource(R.string.tron_defi_unfreezing),
                value = "$unfreezingTrx TRX",
            )
        }
    }
}

@Composable
private fun TronFrozenRow(
    label: String,
    isLoading: Boolean,
    amountTrx: String,
    onClickFreeze: () -> Unit,
    onClickUnfreeze: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = label,
                style = Theme.brockmann.body.s.medium,
                color = Theme.v2.colors.text.secondary,
            )
            if (isLoading) {
                UiPlaceholderLoader(modifier = Modifier.size(width = 80.dp, height = 18.dp))
            } else {
                Text(
                    text = "$amountTrx TRX",
                    style = Theme.brockmann.body.m.medium,
                    color = Theme.v2.colors.text.primary,
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            VsButton(
                label = stringResource(R.string.tron_defi_freeze),
                state = VsButtonState.Enabled,
                size = VsButtonSize.Small,
                onClick = onClickFreeze,
            )
            VsButton(
                label = stringResource(R.string.tron_defi_unfreeze),
                variant = VsButtonVariant.Secondary,
                state = VsButtonState.Enabled,
                size = VsButtonSize.Small,
                onClick = onClickUnfreeze,
            )
        }
    }
}

@Composable
private fun TronResourcesCard(
    isLoading: Boolean,
    availableBandwidth: Long,
    totalBandwidth: Long,
    availableEnergy: Long,
    totalEnergy: Long,
    bandwidthProgress: Float,
    energyProgress: Float,
) {
    Column(
        modifier =
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Theme.v2.colors.backgrounds.secondary)
                .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(R.string.tron_defi_resources),
            style = Theme.brockmann.body.l.medium,
            color = Theme.v2.colors.text.primary,
        )

        TronResourceRow(
            label = stringResource(R.string.tron_defi_bandwidth),
            iconResId = R.drawable.ic_tron_bandwidth,
            isLoading = isLoading,
            available = availableBandwidth,
            total = totalBandwidth,
            progress = bandwidthProgress,
        )

        TronResourceRow(
            label = stringResource(R.string.tron_defi_energy),
            iconResId = R.drawable.energy,
            isLoading = isLoading,
            available = availableEnergy,
            total = totalEnergy,
            progress = energyProgress,
        )
    }
}

@Composable
private fun TronResourceRow(
    label: String,
    iconResId: Int,
    isLoading: Boolean,
    available: Long,
    total: Long,
    progress: Float,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                UiIcon(drawableResId = iconResId, size = 16.dp)
                Text(
                    text = label,
                    style = Theme.brockmann.body.s.medium,
                    color = Theme.v2.colors.text.secondary,
                )
            }

            if (isLoading) {
                UiPlaceholderLoader(modifier = Modifier.size(width = 80.dp, height = 16.dp))
            } else {
                Text(
                    text = "$available / $total",
                    style = Theme.brockmann.body.s.medium,
                    color = Theme.v2.colors.text.primary,
                )
            }
        }

        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
            color = Theme.v2.colors.primary.accent1,
            trackColor = Theme.v2.colors.backgrounds.tertiary,
            strokeCap = StrokeCap.Round,
        )
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

@Composable
private fun TronInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = Theme.brockmann.body.s.medium,
            color = Theme.v2.colors.text.secondary,
        )
        Text(
            text = value,
            style = Theme.brockmann.body.s.medium,
            color = Theme.v2.colors.text.primary,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun TronDeFiPositionsScreenPreview() {
    TronDeFiPositionsScreenContent(
        state =
            TronDeFiUiState(
                isLoading = false,
                availableBalanceTrx = "1234.567890",
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

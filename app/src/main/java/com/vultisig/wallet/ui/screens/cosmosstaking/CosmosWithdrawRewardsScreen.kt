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
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vultisig.wallet.data.blockchain.cosmos.staking.CosmosWithdrawRewardsCandidate
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.buttons.VsButton
import com.vultisig.wallet.ui.components.buttons.VsButtonState
import com.vultisig.wallet.ui.components.buttons.VsButtonVariant
import com.vultisig.wallet.ui.components.v2.scaffold.V2Scaffold
import com.vultisig.wallet.ui.models.cosmosstaking.CosmosWithdrawRewardsViewModel
import com.vultisig.wallet.ui.theme.Theme

/**
 * Selection-driven claim-rewards screen for LUNA / LUNC. Per-validator pending reward list with a
 * checkbox column, a "Select all" toggle, total claim amount, estimated fee (scales with selection
 * count), and an inline insufficient-fee warning when the pre-flight check fails.
 *
 * Mirrors iOS `CosmosWithdrawRewardsTransactionScreen.swift` (vultisig-ios PR #4432).
 */
@Composable
internal fun CosmosWithdrawRewardsScreen(
    viewModel: CosmosWithdrawRewardsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    V2Scaffold(title = "Claim ${state.ticker.ifEmpty { "Rewards" }}") {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                HeaderRow(
                    maxBatchSize = state.maxBatchSize,
                    onSelectAll = viewModel::toggleSelectAll,
                )

                if (state.hitBatchCapWarning) {
                    UiSpacer(size = 8.dp)
                    CapWarning(maxBatchSize = state.maxBatchSize)
                }

                UiSpacer(size = 12.dp)

                when {
                    state.isLoading ->
                        Text(
                            text = "Loading rewards…",
                            style = Theme.brockmann.body.s.medium,
                            color = Theme.v2.colors.text.secondary,
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                        )
                    state.candidates.isEmpty() ->
                        Text(
                            text = "No claimable rewards on this chain",
                            style = Theme.brockmann.body.s.medium,
                            color = Theme.v2.colors.text.secondary,
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                        )
                    else ->
                        LazyColumn(modifier = Modifier.fillMaxWidth().padding(bottom = 220.dp)) {
                            items(state.candidates, key = { it.validatorAddress }) { candidate ->
                                CandidateRow(
                                    candidate = candidate,
                                    isSelected =
                                        state.selectedValidators.contains(
                                            candidate.validatorAddress
                                        ),
                                    ticker = state.ticker,
                                    onToggle = { viewModel.toggle(candidate.validatorAddress) },
                                )
                            }
                        }
                }

                val errorMessage = state.errorMessage
                if (errorMessage != null) {
                    Text(
                        text = errorMessage,
                        color = Theme.v2.colors.alerts.error,
                        style = Theme.brockmann.supplementary.caption,
                    )
                }
            }

            FooterSummary(
                ticker = state.ticker,
                totalSelectedReward = state.totalSelectedReward.toPlainString(),
                estimatedFee = state.estimatedFee.toPlainString(),
                hasSufficientBalanceForFee = state.hasSufficientBalanceForFee,
                isSubmitting = state.isSubmitting,
                isValidForm = state.validForm,
                onSubmit = viewModel::submit,
            )
        }
    }
}

@Composable
private fun HeaderRow(maxBatchSize: Int, onSelectAll: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Select validators (max $maxBatchSize)",
            style = Theme.brockmann.supplementary.caption,
            color = Theme.v2.colors.text.secondary,
        )
        Text(
            text = "Select all",
            style = Theme.brockmann.supplementary.caption,
            color = Theme.v2.colors.primary.accent4,
            modifier = Modifier.clickable(onClick = onSelectAll).padding(horizontal = 6.dp),
        )
    }
}

@Composable
private fun CapWarning(maxBatchSize: Int) {
    Row(
        modifier =
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .border(
                    width = 1.dp,
                    color = Theme.v2.colors.alerts.warning,
                    shape = RoundedCornerShape(8.dp),
                )
                .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "⚠",
            style = Theme.brockmann.supplementary.caption,
            color = Theme.v2.colors.alerts.warning,
        )
        UiSpacer(size = 6.dp)
        Text(
            text =
                "Max $maxBatchSize validators per batch — split additional claims into separate transactions",
            style = Theme.brockmann.supplementary.caption,
            color = Theme.v2.colors.text.secondary,
        )
    }
}

@Composable
private fun CandidateRow(
    candidate: CosmosWithdrawRewardsCandidate,
    isSelected: Boolean,
    ticker: String,
    onToggle: () -> Unit,
) {
    Row(
        modifier =
            Modifier.fillMaxWidth()
                .padding(vertical = 4.dp)
                .clip(RoundedCornerShape(12.dp))
                .border(
                    width = 1.dp,
                    color = Theme.v2.colors.border.normal,
                    shape = RoundedCornerShape(12.dp),
                )
                .clickable(onClick = onToggle)
                .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = isSelected, onCheckedChange = { onToggle() })
        UiSpacer(size = 8.dp)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = candidate.validatorMoniker.ifEmpty { candidate.validatorAddress },
                style = Theme.brockmann.body.s.medium,
                color = Theme.v2.colors.text.primary,
            )
            Text(
                text = candidate.validatorAddress,
                style = Theme.brockmann.supplementary.caption,
                color = Theme.v2.colors.text.secondary,
            )
        }
        Text(
            text = "${candidate.pendingReward.toPlainString()} $ticker",
            style = Theme.brockmann.body.s.medium,
            color = Theme.v2.colors.text.primary,
        )
    }
}

@Composable
private fun androidx.compose.foundation.layout.BoxScope.FooterSummary(
    ticker: String,
    totalSelectedReward: String,
    estimatedFee: String,
    hasSufficientBalanceForFee: Boolean,
    isSubmitting: Boolean,
    isValidForm: Boolean,
    onSubmit: () -> Unit,
) {
    Column(
        modifier =
            Modifier.align(Alignment.BottomCenter)
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                .border(
                    width = 1.dp,
                    color = Theme.v2.colors.border.normal,
                    shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
                )
                .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                text = "Total rewards",
                style = Theme.brockmann.supplementary.caption,
                color = Theme.v2.colors.text.secondary,
            )
            Text(
                text = "$totalSelectedReward $ticker",
                style = Theme.brockmann.body.s.medium,
                color = Theme.v2.colors.text.primary,
            )
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                text = "Estimated fee",
                style = Theme.brockmann.supplementary.caption,
                color = Theme.v2.colors.text.secondary,
            )
            Text(
                text = "$estimatedFee $ticker",
                style = Theme.brockmann.supplementary.caption,
                color = Theme.v2.colors.text.primary,
            )
        }

        if (!hasSufficientBalanceForFee) {
            Text(
                text = "Insufficient $ticker balance to cover the batch fee",
                style = Theme.brockmann.supplementary.caption,
                color = Theme.v2.colors.alerts.error,
            )
        }

        VsButton(
            label = "Continue",
            variant = VsButtonVariant.CTA,
            state =
                if (isSubmitting || !isValidForm) VsButtonState.Disabled else VsButtonState.Enabled,
            onClick = onSubmit,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

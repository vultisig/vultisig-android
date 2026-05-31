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
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.buttons.VsButton
import com.vultisig.wallet.ui.components.buttons.VsButtonState
import com.vultisig.wallet.ui.components.buttons.VsButtonVariant
import com.vultisig.wallet.ui.components.v2.scaffold.V2Scaffold
import com.vultisig.wallet.ui.models.cosmosstaking.CosmosWithdrawRewardsViewModel
import com.vultisig.wallet.ui.theme.Theme

@Composable
internal fun CosmosWithdrawRewardsScreen(
    viewModel: CosmosWithdrawRewardsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    V2Scaffold(title = "Claim ${state.ticker.ifEmpty { "Rewards" }}") {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Pick validators (max ${state.maxBatchSize})",
                        style = Theme.brockmann.body.s.medium,
                        color = Theme.v2.colors.text.primary,
                    )
                    Row {
                        Text(
                            text = "Select all",
                            style = Theme.brockmann.supplementary.caption,
                            color = Theme.v2.colors.primary.accent4,
                            modifier =
                                Modifier.clickable(onClick = viewModel::selectAll)
                                    .padding(horizontal = 6.dp),
                        )
                        Text(
                            text = "Clear",
                            style = Theme.brockmann.supplementary.caption,
                            color = Theme.v2.colors.text.secondary,
                            modifier =
                                Modifier.clickable(onClick = viewModel::clearSelection)
                                    .padding(horizontal = 6.dp),
                        )
                    }
                }

                when {
                    state.isLoading ->
                        Text(
                            text = "Loading rewards…",
                            style = Theme.brockmann.body.s.medium,
                            color = Theme.v2.colors.text.secondary,
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                        )
                    state.rewards.isEmpty() ->
                        Text(
                            text = "No claimable rewards on this chain",
                            style = Theme.brockmann.body.s.medium,
                            color = Theme.v2.colors.text.secondary,
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                        )
                    else ->
                        LazyColumn(modifier = Modifier.fillMaxWidth().padding(bottom = 80.dp)) {
                            items(state.rewards, key = { it.validatorAddress }) { reward ->
                                val isSelected =
                                    state.selectedValidators.contains(reward.validatorAddress)
                                val canSelect = isSelected || !state.isAtCap
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
                                            .clickable(enabled = canSelect) {
                                                viewModel.toggleValidator(reward.validatorAddress)
                                            }
                                            .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Checkbox(
                                        checked = isSelected,
                                        onCheckedChange = { _ ->
                                            if (canSelect) {
                                                viewModel.toggleValidator(reward.validatorAddress)
                                            }
                                        },
                                    )
                                    UiSpacer(size = 8.dp)
                                    Column(modifier = Modifier.fillMaxWidth()) {
                                        Text(
                                            text = reward.validatorAddress,
                                            style = Theme.brockmann.supplementary.caption,
                                            color = Theme.v2.colors.text.secondary,
                                        )
                                        val totalDenom = reward.reward.firstOrNull()
                                        if (totalDenom != null) {
                                            Text(
                                                text = "${totalDenom.amount} ${totalDenom.denom}",
                                                style = Theme.brockmann.body.s.medium,
                                                color = Theme.v2.colors.text.primary,
                                            )
                                        }
                                    }
                                }
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

            VsButton(
                label = "Claim ${state.selectedValidators.size}/${state.maxBatchSize}",
                variant = VsButtonVariant.CTA,
                state =
                    if (state.isSubmitting || state.selectedValidators.isEmpty())
                        VsButtonState.Disabled
                    else VsButtonState.Enabled,
                onClick = viewModel::submit,
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(16.dp),
            )
        }
    }
}

package com.vultisig.wallet.ui.screens.cosmosstaking

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.buttons.VsButton
import com.vultisig.wallet.ui.components.buttons.VsButtonState
import com.vultisig.wallet.ui.components.buttons.VsButtonVariant
import com.vultisig.wallet.ui.components.v2.scaffold.V2Scaffold
import com.vultisig.wallet.ui.models.cosmosstaking.CosmosUndelegateViewModel
import com.vultisig.wallet.ui.theme.Theme

/**
 * Undelegate input form for LUNA / LUNC. Same shape as the iOS `CosmosUndelegateTransactionScreen`
 * minus the validator picker — the validator is pre-selected by the caller (from the position card)
 * and surfaced as read-only. The 21-day unbonding-lock notice is inline so the user accepts the
 * lock before confirming.
 */
@Composable
internal fun CosmosUndelegateScreen(viewModel: CosmosUndelegateViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    V2Scaffold(
        title =
            stringResource(
                R.string.cosmos_staking_undelegate_title,
                state.ticker.ifEmpty { stringResource(R.string.cosmos_staking_token_fallback) },
            )
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ValidatorReadonlyBlock(
                    moniker = state.validatorMoniker,
                    address = state.validatorAddress,
                )

                StakingAmountCard(
                    ticker = state.ticker,
                    amountFieldState = viewModel.amountFieldState,
                    available = state.stakedBalance,
                    percentageSelected = state.percentageSelected,
                    onPercentage = viewModel::onPercentageChange,
                )

                val unbondingMsg = state.unbondingLockMessage
                if (unbondingMsg != null) {
                    UnbondingLockNotice(message = unbondingMsg)
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
                label = stringResource(R.string.cosmos_staking_continue),
                variant = VsButtonVariant.CTA,
                state = if (state.isSubmitting) VsButtonState.Disabled else VsButtonState.Enabled,
                onClick = viewModel::submit,
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(16.dp),
            )
        }
    }
}

@Composable
internal fun ValidatorReadonlyBlock(moniker: String, address: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = stringResource(R.string.cosmos_staking_validator_picker),
            style = Theme.brockmann.body.s.medium,
            color = Theme.v2.colors.text.primary,
        )
        Text(
            text = moniker.ifEmpty { address },
            style = Theme.brockmann.body.s.medium,
            color = Theme.v2.colors.text.primary,
        )
        Text(
            text = address,
            style = Theme.brockmann.supplementary.caption,
            color = Theme.v2.colors.text.secondary,
        )
    }
}

@Composable
internal fun UnbondingLockNotice(message: String) {
    Row(
        modifier =
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .border(
                    width = 1.dp,
                    color = Theme.v2.colors.alerts.warning,
                    shape = RoundedCornerShape(12.dp),
                )
                .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "⚠",
            style = Theme.brockmann.body.s.medium,
            color = Theme.v2.colors.alerts.warning,
        )
        UiSpacer(size = 8.dp)
        Text(
            text = message,
            style = Theme.brockmann.supplementary.caption,
            color = Theme.v2.colors.text.primary,
        )
    }
}

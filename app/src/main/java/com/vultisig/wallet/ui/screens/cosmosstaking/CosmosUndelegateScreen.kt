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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
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
    V2Scaffold(title = "Unstake ${state.ticker.ifEmpty { "Token" }}") {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ValidatorReadonlyBlock(
                    moniker = state.validatorMoniker,
                    address = state.validatorAddress,
                )

                AmountBlock(
                    ticker = state.ticker,
                    amountText = viewModel.amountFieldState.text.toString(),
                    onAmountChange = { v ->
                        viewModel.amountFieldState.edit { replace(0, length, v) }
                    },
                )

                PercentagePicker(
                    selected = state.percentageSelected,
                    onSelect = viewModel::onPercentageChange,
                )

                StakedBalanceRow(
                    staked = state.stakedBalance.toPlainString(),
                    ticker = state.ticker,
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
                label = "Continue",
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
            text = "Validator",
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
internal fun AmountBlock(ticker: String, amountText: String, onAmountChange: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "Amount (${ticker.ifEmpty { "Token" }})",
            style = Theme.brockmann.body.s.medium,
            color = Theme.v2.colors.text.primary,
        )
        BasicTextField(
            value = amountText,
            onValueChange = onAmountChange,
            singleLine = true,
            textStyle =
                TextStyle(
                    color = Theme.v2.colors.text.primary,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.SemiBold,
                ),
            modifier =
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .border(
                        width = 1.dp,
                        color = Theme.v2.colors.border.normal,
                        shape = RoundedCornerShape(12.dp),
                    )
                    .padding(horizontal = 16.dp, vertical = 14.dp),
        )
    }
}

@Composable
internal fun PercentagePicker(selected: Int, onSelect: (Int) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(25, 50, 75, 100).forEach { percent ->
            val isActive = selected == percent
            Box(
                modifier =
                    Modifier.weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .border(
                            width = if (isActive) 2.dp else 1.dp,
                            color =
                                if (isActive) Theme.v2.colors.primary.accent4
                                else Theme.v2.colors.border.normal,
                            shape = RoundedCornerShape(10.dp),
                        )
                        .clickable { onSelect(percent) }
                        .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (percent == 100) "Max" else "$percent%",
                    style = Theme.brockmann.body.s.medium,
                    color =
                        if (isActive) Theme.v2.colors.primary.accent4
                        else Theme.v2.colors.text.primary,
                )
            }
        }
    }
}

@Composable
internal fun StakedBalanceRow(staked: String, ticker: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Staked",
            style = Theme.brockmann.body.s.medium,
            color = Theme.v2.colors.text.secondary,
        )
        Text(
            text = "$staked $ticker",
            style = Theme.brockmann.body.s.medium,
            color = Theme.v2.colors.text.primary,
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

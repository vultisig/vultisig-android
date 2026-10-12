package com.vultisig.wallet.ui.screens.v2.defi.ton

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.buttons.VsButton
import com.vultisig.wallet.ui.components.buttons.VsButtonState
import com.vultisig.wallet.ui.components.v2.scaffold.V2Scaffold
import com.vultisig.wallet.ui.models.defi.TonLiquidUnstakeViewModel
import com.vultisig.wallet.ui.models.deposit.submit.TONSTAKERS_POOL_NAME
import com.vultisig.wallet.ui.screens.cosmosstaking.StakingAmountCard
import com.vultisig.wallet.ui.theme.Theme
import com.vultisig.wallet.ui.utils.asString
import com.vultisig.wallet.ui.utils.formatTokenAmount
import java.math.BigDecimal

/** Tonstakers liquid-staking withdrawal: amount in tsTON, previewed as the TON the pool pays. */
@Composable
internal fun TonLiquidUnstakeScreen(viewModel: TonLiquidUnstakeViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    val nativeTicker = state.nativeTicker.ifEmpty { TON_NATIVE_TICKER_FALLBACK }

    V2Scaffold(
        title = stringResource(R.string.ton_unstake_title, state.ticker),
        onBackClick = viewModel::back,
    ) {
        val amount = viewModel.amountFieldState.text.toString().trim().toBigDecimalOrNull()
        val hasAmount = amount != null && amount > BigDecimal.ZERO
        val aboveBalance = amount != null && amount > state.availableTsTon
        val canContinue =
            state.isReady &&
                state.hasSufficientNativeBalance &&
                hasAmount &&
                !aboveBalance &&
                !state.isSubmitting

        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(16.dp).padding(bottom = 80.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                StakingAmountCard(
                    ticker = state.ticker,
                    amountFieldState = viewModel.amountFieldState,
                    available = state.availableTsTon,
                    percentageSelected = state.percentageSelected,
                    onPercentage = viewModel::onPercentageChange,
                    modifier = Modifier.weight(1f),
                )

                if (aboveBalance) {
                    Text(
                        text = stringResource(R.string.insufficient_native_token, state.ticker),
                        style = Theme.brockmann.supplementary.caption,
                        color = Theme.v2.colors.alerts.error,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                    )
                }

                TonLiquidInfoCard(
                    rows =
                        buildList {
                            add(
                                TonLiquidInfoRow(
                                    title = stringResource(R.string.ton_staking_pool_header),
                                    value = TONSTAKERS_POOL_NAME,
                                )
                            )
                            state.expectedTon?.let { expected ->
                                add(
                                    TonLiquidInfoRow(
                                        title = stringResource(R.string.ton_liquid_you_receive),
                                        value =
                                            "≈ " +
                                                expected
                                                    .stripTrailingZeros()
                                                    .formatTokenAmount(nativeTicker),
                                    )
                                )
                            }
                            add(
                                TonLiquidInfoRow(
                                    title = stringResource(R.string.ton_liquid_attached_value),
                                    value =
                                        state.attachedValue
                                            .stripTrailingZeros()
                                            .formatTokenAmount(nativeTicker),
                                )
                            )
                        }
                )

                Text(
                    text =
                        stringResource(
                            // The pool only pays out early while it runs in optimistic mode.
                            if (state.isOptimistic) R.string.ton_liquid_unstake_notice
                            else R.string.ton_liquid_unstake_notice_round_end,
                            state.attachedValue.stripTrailingZeros().formatTokenAmount(),
                            nativeTicker,
                        ),
                    style = Theme.brockmann.supplementary.caption,
                    color = Theme.v2.colors.text.secondary,
                )

                if (state.isReady && !state.hasSufficientNativeBalance) {
                    Text(
                        text = stringResource(R.string.insufficient_native_token, nativeTicker),
                        style = Theme.brockmann.supplementary.caption,
                        color = Theme.v2.colors.alerts.error,
                    )
                }

                state.errorMessage?.let { error ->
                    Text(
                        text = error.asString(),
                        style = Theme.brockmann.supplementary.caption,
                        color = Theme.v2.colors.alerts.error,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                    )
                }
            }

            VsButton(
                label = stringResource(R.string.cosmos_staking_continue),
                state = if (canContinue) VsButtonState.Enabled else VsButtonState.Disabled,
                onClick = viewModel::submit,
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(16.dp),
            )
        }
    }
}

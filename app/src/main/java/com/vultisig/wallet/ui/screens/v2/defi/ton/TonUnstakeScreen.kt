package com.vultisig.wallet.ui.screens.v2.defi.ton

import androidx.compose.foundation.background
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.UiHorizontalDivider
import com.vultisig.wallet.ui.components.buttons.VsButton
import com.vultisig.wallet.ui.components.buttons.VsButtonState
import com.vultisig.wallet.ui.components.buttons.VsButtonVariant
import com.vultisig.wallet.ui.components.v2.scaffold.V2Scaffold
import com.vultisig.wallet.ui.models.defi.TonUnstakeViewModel
import com.vultisig.wallet.ui.theme.Theme
import com.vultisig.wallet.ui.utils.asString

/** TON nominator-pool unstake confirmation. Mirrors iOS `TonUnstakeTransactionScreen`. */
@Composable
internal fun TonUnstakeScreen(viewModel: TonUnstakeViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    val ticker = state.ticker.ifEmpty { "TON" }

    V2Scaffold(
        title = stringResource(R.string.ton_unstake_title, ticker),
        onBackClick = viewModel::back,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
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
                    InfoRow(
                        title = stringResource(R.string.ton_defi_staked_amount, ticker),
                        value = state.stakedDisplay,
                    )
                    UiHorizontalDivider()
                    InfoRow(
                        title = stringResource(R.string.ton_staking_pool_address),
                        value = state.poolAddress,
                        truncateMiddle = true,
                    )
                }

                Text(
                    text = stringResource(R.string.ton_unstake_full_withdrawal_notice),
                    style = Theme.brockmann.supplementary.caption,
                    color = Theme.v2.colors.text.secondary,
                )

                if (!state.hasSufficientBalance) {
                    Text(
                        text = stringResource(R.string.insufficient_native_token, ticker),
                        style = Theme.brockmann.supplementary.caption,
                        color = Theme.v2.colors.alerts.error,
                    )
                }

                state.errorMessage?.let { error ->
                    Text(
                        text = error.asString(),
                        style = Theme.brockmann.supplementary.caption,
                        color = Theme.v2.colors.alerts.error,
                    )
                }
            }

            VsButton(
                label = stringResource(R.string.cosmos_staking_continue),
                variant = VsButtonVariant.CTA,
                state =
                    if (state.hasSufficientBalance && !state.isSubmitting) VsButtonState.Enabled
                    else VsButtonState.Disabled,
                isLoading = state.isSubmitting,
                onClick = viewModel::submit,
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(16.dp),
            )
        }
    }
}

@Composable
private fun InfoRow(title: String, value: String, truncateMiddle: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = title,
            style = Theme.brockmann.body.s.medium,
            color = Theme.v2.colors.text.tertiary,
        )
        Text(
            text = value,
            style = Theme.brockmann.body.m.medium,
            color = Theme.v2.colors.text.primary,
            maxLines = 1,
            overflow = if (truncateMiddle) TextOverflow.MiddleEllipsis else TextOverflow.Ellipsis,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f),
        )
    }
}

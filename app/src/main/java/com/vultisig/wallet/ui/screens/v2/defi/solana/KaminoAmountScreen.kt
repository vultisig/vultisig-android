package com.vultisig.wallet.ui.screens.v2.defi.solana

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vultisig.wallet.R
import com.vultisig.wallet.data.blockchain.solana.kamino.KaminoWithdrawEligibility
import com.vultisig.wallet.data.blockchain.solana.kamino.KaminoWithdrawLiquidity
import com.vultisig.wallet.ui.components.buttons.VsButton
import com.vultisig.wallet.ui.components.buttons.VsButtonState
import com.vultisig.wallet.ui.components.buttons.VsButtonVariant
import com.vultisig.wallet.ui.components.v2.scaffold.V2Scaffold
import com.vultisig.wallet.ui.models.solanastaking.KaminoAmountUiState
import com.vultisig.wallet.ui.models.solanastaking.KaminoAmountViewModel
import com.vultisig.wallet.ui.screens.cosmosstaking.StakingAmountCard
import com.vultisig.wallet.ui.theme.Theme
import com.vultisig.wallet.ui.utils.asString
import com.vultisig.wallet.ui.utils.formatTokenAmount
import java.math.BigDecimal

/**
 * Amount entry for a Kamino Earn deposit or withdraw: the shared amount card with 25/50/75/Max
 * chips over the available balance, then Continue. On Continue the ViewModel builds the transaction
 * — memo and compute budget included — validates it, and routes to the verify screen.
 */
@Composable
internal fun KaminoAmountScreen(viewModel: KaminoAmountViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()

    V2Scaffold(
        title =
            stringResource(
                if (state.isWithdraw) R.string.kamino_withdraw_title
                else R.string.kamino_deposit_title
            ),
        onBackClick = viewModel::back,
    ) {
        KaminoAmountContent(
            state = state,
            amountFieldState = viewModel.amountFieldState,
            onPercentage = viewModel::onPercentageChange,
            onAmountChanged = viewModel::onAmountChanged,
            onSubmit = viewModel::submit,
        )
    }
}

@Composable
internal fun KaminoAmountContent(
    state: KaminoAmountUiState,
    amountFieldState: TextFieldState,
    onPercentage: (Int) -> Unit,
    onAmountChanged: (BigDecimal?) -> Unit = {},
    onSubmit: () -> Unit,
) {
    val amount = amountFieldState.text.toString().toBigDecimalOrNull()

    // Recomputed as the user types, and again when the vault's figures land: keying only on the
    // amount left a notice decided against a buffer that had not loaded yet.
    LaunchedEffect(amount, state.isLoading, state.available) { onAmountChanged(amount) }

    // A staked position is withdrawable — Kamino releases the shares from the farm as part of the
    // transaction. What remains is about the read: nothing held, or figures that cannot be trusted.
    val blockingReason: String? =
        when (state.eligibility) {
            KaminoWithdrawEligibility.Empty -> stringResource(R.string.kamino_withdraw_nothing)
            KaminoWithdrawEligibility.Unreadable ->
                stringResource(R.string.kamino_withdraw_unreadable)
            else -> null
        }

    // Over-balance first, then the vault's own floor — the order the user hits them in.
    val validationError: String? =
        when {
            amount == null -> null
            amount.signum() <= 0 -> null
            // While the form is loading `available` is still zero, so validating against it would
            // tell the user every amount exceeds their balance.
            state.isLoading -> null
            amount > state.available -> stringResource(R.string.kamino_amount_exceeds_available)
            state.minimum != null && amount < state.minimum ->
                stringResource(
                    R.string.kamino_amount_below_minimum,
                    state.minimum.stripTrailingZeros().formatTokenAmount(),
                    state.ticker,
                )
            else -> null
        }

    val canContinue =
        amount != null &&
            amount.signum() > 0 &&
            validationError == null &&
            blockingReason == null &&
            !state.isSubmitting &&
            !state.isLoading

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp).padding(bottom = 80.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = state.vaultName,
                style = Theme.brockmann.body.m.medium,
                color = Theme.v2.colors.text.primary,
            )

            StakingAmountCard(
                ticker = state.ticker,
                amountFieldState = amountFieldState,
                available = state.available,
                percentageSelected = state.percentageSelected,
                onPercentage = onPercentage,
                modifier = Modifier.weight(1f),
            )

            (blockingReason ?: validationError ?: state.error?.asString())?.let { message ->
                Text(
                    text = message,
                    style = Theme.brockmann.supplementary.caption,
                    color = Theme.v2.colors.alerts.error,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                )
            }

            // Ordinary information about the vault, not a failure: the liquid buffer is well under
            // 1% of assets, so a withdraw exceeding it is the common case.
            (state.liquidity as? KaminoWithdrawLiquidity.Delayed)?.let { delayed ->
                Text(
                    text =
                        stringResource(
                            R.string.kamino_withdraw_delayed,
                            BigDecimal(delayed.available.baseUnits)
                                .movePointLeft(delayed.available.decimals)
                                .stripTrailingZeros()
                                .formatTokenAmount(),
                            state.ticker,
                        ),
                    style = Theme.brockmann.supplementary.caption,
                    color = Theme.v2.colors.alerts.warning,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                )
            }
        }

        VsButton(
            label = stringResource(R.string.cosmos_staking_continue),
            variant = VsButtonVariant.CTA,
            state = if (canContinue) VsButtonState.Enabled else VsButtonState.Disabled,
            isLoading = state.isSubmitting,
            onClick = onSubmit,
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(16.dp),
        )
    }
}

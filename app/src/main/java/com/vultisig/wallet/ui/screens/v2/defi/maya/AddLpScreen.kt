package com.vultisig.wallet.ui.screens.v2.defi.maya

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.PercentageChip
import com.vultisig.wallet.ui.components.TokenAmountInput
import com.vultisig.wallet.ui.components.TokenFiatToggle
import com.vultisig.wallet.ui.components.UiGradientHorizontalDivider
import com.vultisig.wallet.ui.components.UiIcon
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.buttons.VsButton
import com.vultisig.wallet.ui.components.buttons.VsButtonState
import com.vultisig.wallet.ui.components.buttons.VsButtonVariant
import com.vultisig.wallet.ui.models.deposit.DepositFormViewModel
import com.vultisig.wallet.ui.screens.send.EstimatedNetworkFee
import com.vultisig.wallet.ui.screens.v2.defi.model.DeFiNavActions
import com.vultisig.wallet.ui.theme.Theme
import com.vultisig.wallet.ui.utils.asString
import java.math.BigDecimal

@Composable
internal fun AddLpScreen(
    vaultId: String,
    chainId: String,
    poolId: String,
    viewModel: DepositFormViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val fiatCurrency by viewModel.fiatCurrency.collectAsState()

    LaunchedEffect(vaultId, chainId, poolId) {
        viewModel.loadData(
            vaultId = vaultId,
            chainId = chainId,
            depositType = DeFiNavActions.ADD_LP.type,
            bondAddress = null,
            poolId = poolId,
        )
    }

    AddLpContent(
        tokenSymbol = state.selectedToken.ticker,
        fiatCurrency = fiatCurrency,
        tokenAmountFieldState = viewModel.tokenAmountFieldState,
        fiatAmountFieldState = viewModel.fiatAmountFieldState,
        fiatAmount = null,
        totalGas = state.totalGas.asString(),
        estimatedFee = state.estimatedFee.asString(),
        balance = state.balance.asString(),
        rawBalance = state.balanceDecimal,
        isLoading = state.isLoading,
        tokenAmountError = state.tokenAmountError?.asString(),
        onDeposit = {
            viewModel.validateTokenAmount()
            if (viewModel.state.value.tokenAmountError == null) {
                viewModel.deposit()
            }
        },
    )
}

@Composable
private fun AddLpContent(
    tokenSymbol: String,
    fiatCurrency: String,
    tokenAmountFieldState: TextFieldState,
    fiatAmountFieldState: TextFieldState,
    fiatAmount: String?,
    totalGas: String,
    estimatedFee: String,
    balance: String,
    rawBalance: BigDecimal? = null,
    isLoading: Boolean,
    tokenAmountError: String?,
    onDeposit: () -> Unit,
    initialSelectedPercentageIndex: Int = -1,
) {
    var selectedPercentageIndex by remember { mutableIntStateOf(initialSelectedPercentageIndex) }
    var usingTokenAmountInput by remember { mutableStateOf(true) }

    LaunchedEffect(balance) { selectedPercentageIndex = initialSelectedPercentageIndex }

    val percentages = listOf(25, 50, 75, 100)
    val percentageLabels = listOf("25%", "50%", "75%", stringResource(R.string.max))

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
            // Am useount card
            Column(
                modifier =
                    Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .border(
                            width = 1.dp,
                            color = Theme.v2.colors.border.normal,
                            shape = RoundedCornerShape(12.dp),
                        )
                        .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Amount label + fuel icon row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.add_pool_amount_label),
                        style = Theme.brockmann.body.s.medium,
                        color = Theme.v2.colors.text.primary,
                    )
                    UiIcon(
                        drawableResId = R.drawable.gas,
                        size = 16.dp,
                        tint = Theme.v2.colors.text.secondary,
                    )
                }

                UiGradientHorizontalDivider()

                // Large amount input area
                Box(modifier = Modifier.fillMaxWidth().height(211.dp)) {
                    val primaryFieldState: TextFieldState
                    val primaryLabel: String
                    val secondaryText: String

                    if (usingTokenAmountInput) {
                        primaryFieldState = tokenAmountFieldState
                        primaryLabel = tokenSymbol
                        secondaryText = "${fiatAmountFieldState.text.ifEmpty { "0" }} $fiatCurrency"
                    } else {
                        primaryFieldState = fiatAmountFieldState
                        primaryLabel = fiatCurrency
                        secondaryText = "${tokenAmountFieldState.text.ifEmpty { "0" }} $tokenSymbol"
                    }

                    TokenAmountInput(
                        primaryFieldState = primaryFieldState,
                        primaryLabel = primaryLabel,
                        secondaryText = secondaryText,
                        maxBalance = if (usingTokenAmountInput) rawBalance else null,
                        modifier = Modifier.padding(horizontal = 54.dp).align(Alignment.Center),
                    )

                    TokenFiatToggle(
                        isTokenSelected = usingTokenAmountInput,
                        onTokenSelected = { isToken -> usingTokenAmountInput = isToken },
                        modifier = Modifier.align(Alignment.CenterEnd),
                    )
                }

                // Percentage chips row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    percentageLabels.forEachIndexed { index, label ->
                        PercentageChip(
                            title = label,
                            isSelected = selectedPercentageIndex == index,
                            onClick = {
                                if (rawBalance != null) {
                                    selectedPercentageIndex = index
                                    val percent = percentages[index]
                                    val amount =
                                        rawBalance
                                            .multiply(percent.toBigDecimal())
                                            .divide(100.toBigDecimal())
                                            .stripTrailingZeros()
                                            .toPlainString()
                                    tokenAmountFieldState.setTextAndPlaceCursorAtEnd(amount)
                                }
                            },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }

                // Balance available row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.send_form_balance_available),
                        style = Theme.brockmann.body.s.medium,
                        color = Theme.v2.colors.text.primary,
                    )
                    Text(
                        text = balance,
                        style = Theme.brockmann.body.s.medium,
                        color = Theme.v2.colors.text.secondary,
                    )
                }

                UiGradientHorizontalDivider()

                // Gas row
                EstimatedNetworkFee(
                    tokenGas = totalGas,
                    fiatGas = estimatedFee,
                    title = stringResource(R.string.add_pool_gas_auto),
                )
            }

            if (tokenAmountError != null) {
                UiSpacer(size = 8.dp)
                Text(
                    text = tokenAmountError,
                    style = Theme.brockmann.supplementary.caption,
                    color = Theme.v2.colors.alerts.error,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
        }

        VsButton(
            label = stringResource(R.string.send_continue_button),
            variant = VsButtonVariant.CTA,
            state = if (isLoading) VsButtonState.Disabled else VsButtonState.Enabled,
            onClick = onDeposit,
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(16.dp),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun AddLpContentPreview() {
    Box(modifier = Modifier.background(Theme.v2.colors.backgrounds.primary)) {
        AddLpContent(
            tokenSymbol = "CACAO",
            fiatCurrency = "USD",
            tokenAmountFieldState = TextFieldState("50"),
            fiatAmountFieldState = TextFieldState("2,000.56"),
            fiatAmount = null,
            totalGas = "0.04103261 CACAO",
            estimatedFee = "$0.08",
            balance = "24,052 CACAO",
            isLoading = false,
            tokenAmountError = null,
            onDeposit = {},
            initialSelectedPercentageIndex = 2,
        )
    }
}

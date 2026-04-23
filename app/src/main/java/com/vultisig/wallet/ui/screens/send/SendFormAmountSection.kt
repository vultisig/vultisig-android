package com.vultisig.wallet.ui.screens.send

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.PercentageChip
import com.vultisig.wallet.ui.components.TokenAmountInput
import com.vultisig.wallet.ui.components.TokenFiatToggle
import com.vultisig.wallet.ui.components.UiIcon
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.inputs.VsTextInputField
import com.vultisig.wallet.ui.models.send.AmountFraction
import com.vultisig.wallet.ui.models.send.SendFormUiModel
import com.vultisig.wallet.ui.models.send.SendSections
import com.vultisig.wallet.ui.screens.deposit.components.AutoCompoundToggle
import com.vultisig.wallet.ui.screens.v2.defi.model.DeFiNavActions
import com.vultisig.wallet.ui.theme.Theme
import com.vultisig.wallet.ui.utils.VsClipboardService
import com.vultisig.wallet.ui.utils.asString
import java.math.BigDecimal
import java.math.RoundingMode

@Immutable
internal data class AmountInputs(
    val tokenAmountFieldState: TextFieldState,
    val fiatAmountFieldState: TextFieldState,
    val focusRequester: FocusRequester,
    val onToggleAmountInputType: (Boolean) -> Unit,
    val onChoosePercentageAmount: (AmountFraction) -> Unit,
    val onChooseMaxTokenAmount: () -> Unit,
    val onTokenAmountLostFocus: () -> Unit = {},
)

@Immutable
internal data class OptionalInputs(
    val memoFieldState: TextFieldState,
    val operatorFeeFieldState: TextFieldState,
    val slippageFieldState: TextFieldState,
    val onAutoCompoundCheckedChange: (Boolean) -> Unit,
)

@Composable
internal fun FoldableAmountWidget(
    state: SendFormUiModel,
    addressFieldState: TextFieldState,
    onExpandSection: (SendSections) -> Unit,
    onGasSettingsClick: () -> Unit,
    focusManager: FocusManager,
    onSend: () -> Unit,
    amountInputs: AmountInputs,
    optionalInputs: OptionalInputs,
) {
    val tokenAmountFieldState = amountInputs.tokenAmountFieldState
    val fiatAmountFieldState = amountInputs.fiatAmountFieldState
    val amountFocusRequester = amountInputs.focusRequester
    val onToggleAmountInputType = amountInputs.onToggleAmountInputType
    val onChoosePercentageAmount = amountInputs.onChoosePercentageAmount
    val onChooseMaxTokenAmount = amountInputs.onChooseMaxTokenAmount
    val onTokenAmountLostFocus = amountInputs.onTokenAmountLostFocus
    val memoFieldState = optionalInputs.memoFieldState
    val operatorFeeFieldState = optionalInputs.operatorFeeFieldState
    val slippageTextFieldState = optionalInputs.slippageFieldState
    val onAutoCompoundCheckedChange = optionalInputs.onAutoCompoundCheckedChange
    val isCircleMode =
        state.defiType == DeFiNavActions.DEPOSIT_USDC_CIRCLE ||
            state.defiType == DeFiNavActions.WITHDRAW_USDC_CIRCLE

    FoldableSection(
        expanded = isCircleMode || state.expandedSection == SendSections.Amount,
        onToggle = {
            if (
                !isCircleMode && state.isDstAddressComplete && addressFieldState.text.isNotEmpty()
            ) {
                onExpandSection(SendSections.Amount)
            }
        },
        expandedTitleActions = {
            if (!isCircleMode && state.hasGasSettings) {
                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.weight(1f)) {
                    UiIcon(
                        drawableResId = R.drawable.advance_gas_settings,
                        size = 16.dp,
                        tint = Theme.v2.colors.text.primary,
                        onClick = onGasSettingsClick,
                    )
                }
            }
        },
        title = stringResource(R.string.send_amount),
    ) {
        val contentPadding =
            Modifier.padding(start = 12.dp, top = 16.dp, end = 12.dp, bottom = 12.dp)
        Column(
            modifier =
                if (isCircleMode) Modifier.fillMaxHeight().then(contentPadding) else contentPadding
        ) {
            Box(
                modifier =
                    if (isCircleMode) Modifier.weight(1f).fillMaxWidth()
                    else Modifier.height(211.dp).fillMaxWidth()
            ) {
                val primaryAmountText: String
                val secondaryAmountText: String
                val primaryFieldState: TextFieldState
                val secondaryFieldState: TextFieldState

                if (state.usingTokenAmountInput) {
                    primaryAmountText = state.selectedCoin?.title ?: ""
                    secondaryAmountText = state.fiatCurrency
                    primaryFieldState = tokenAmountFieldState
                    secondaryFieldState = fiatAmountFieldState
                } else {
                    primaryAmountText = state.fiatCurrency
                    secondaryAmountText = state.selectedCoin?.title ?: ""
                    primaryFieldState = fiatAmountFieldState
                    secondaryFieldState = tokenAmountFieldState
                }

                val maxBalance =
                    if (isCircleMode) state.selectedCoin?.balance?.toBigDecimalOrNull() else null

                val secondaryText =
                    if (isCircleMode) {
                        val entered = tokenAmountFieldState.text.toString().toBigDecimalOrNull()
                        val balance = state.selectedCoin?.balance?.toBigDecimalOrNull()
                        if (entered != null && balance != null && balance > BigDecimal.ZERO) {
                            val pct =
                                entered
                                    .multiply(BigDecimal(100))
                                    .divide(balance, 1, RoundingMode.DOWN)
                                    .coerceAtLeast(BigDecimal.ZERO)
                            "%.1f%%".format(pct)
                        } else ""
                    } else {
                        "${secondaryFieldState.text.ifEmpty { "0" }} $secondaryAmountText"
                    }

                TokenAmountInput(
                    primaryFieldState = primaryFieldState,
                    primaryLabel = primaryAmountText,
                    secondaryText = secondaryText,
                    maxBalance = maxBalance,
                    focusRequester = amountFocusRequester,
                    onLostFocus = onTokenAmountLostFocus,
                    onKeyboardAction = {
                        focusManager.clearFocus()
                        onSend()
                    },
                    modifier =
                        Modifier.padding(horizontal = 54.dp)
                            .align(Alignment.Center)
                            .testTag("SendFormScreen.amountField"),
                )

                if (!isCircleMode) {
                    TokenFiatToggle(
                        isTokenSelected = state.usingTokenAmountInput,
                        onTokenSelected = { onToggleAmountInputType(it) },
                        modifier = Modifier.align(Alignment.CenterEnd),
                    )
                }
            }

            UiSpacer(12.dp)

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                state.amountFractionEntries.forEach { fraction ->
                    PercentageChip(
                        title = fraction.title.asString(),
                        isSelected = fraction == state.selectedAmountFraction,
                        onClick = {
                            if (fraction == AmountFraction.F100) {
                                onChooseMaxTokenAmount()
                            } else {
                                onChoosePercentageAmount(fraction)
                            }
                        },
                        modifier = Modifier.weight(1f),
                        isLoading = state.isAmountSelectionLoading,
                    )
                }
            }

            UiSpacer(12.dp)

            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier =
                    if (isCircleMode) Modifier.padding(all = 16.dp)
                    else
                        Modifier.background(
                                color = Theme.v2.colors.backgrounds.secondary,
                                shape = RoundedCornerShape(12.dp),
                            )
                            .padding(all = 16.dp),
            ) {
                Text(
                    text = stringResource(R.string.send_form_balance_available),
                    style = Theme.brockmann.body.s.medium,
                    color = Theme.v2.colors.text.primary,
                )

                val ticker = state.selectedCoin?.title?.let { " $it" } ?: ""
                val balanceText =
                    state.tronBalanceAvailableOverride ?: state.selectedCoin?.balance ?: "0"

                Text(
                    text = balanceText + ticker,
                    style = Theme.brockmann.body.s.medium,
                    color = Theme.v2.colors.text.secondary,
                    textAlign = TextAlign.End,
                    modifier = Modifier.weight(1f),
                )
            }

            UiSpacer(12.dp)

            // memo
            if (state.hasMemo && state.defiType == null) {
                var isMemoExpanded by remember { mutableStateOf(false) }

                val rotationAngle by
                    animateFloatAsState(
                        targetValue = if (isMemoExpanded) 180f else 0f,
                        animationSpec = tween(durationMillis = 200),
                        label = "caretRotation",
                    )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier =
                        Modifier.clickable { isMemoExpanded = !isMemoExpanded }
                            .padding(vertical = 2.dp),
                ) {
                    Text(
                        text = stringResource(R.string.send_form_add_memo),
                        style = Theme.brockmann.supplementary.caption,
                        color = Theme.v2.colors.text.tertiary,
                        modifier = Modifier.weight(1f),
                    )

                    UiIcon(
                        drawableResId = R.drawable.ic_caret_down,
                        tint = Theme.v2.colors.text.primary,
                        size = 16.dp,
                        modifier = Modifier.rotate(rotationAngle),
                    )
                }

                UiSpacer(12.dp)

                AnimatedVisibility(visible = isMemoExpanded) {
                    val clipboardData = VsClipboardService.getClipboardData()
                    Column {
                        VsTextInputField(
                            textFieldState = memoFieldState,
                            hint = stringResource(R.string.send_form_enter_memo),
                            autoCorrectEnabled = false,
                            trailingIcon = R.drawable.paste,
                            onTrailingIconClick = {
                                clipboardData.value
                                    ?.takeIf { it.isNotEmpty() }
                                    ?.let { memoFieldState.setTextAndPlaceCursorAtEnd(text = it) }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )

                        UiSpacer(12.dp)
                    }
                }
            }

            if (state.defiType == DeFiNavActions.BOND) {
                Column(modifier = Modifier.padding(vertical = 2.dp)) {
                    Text(
                        text = stringResource(R.string.bond_operator_fees_basis_point),
                        style = Theme.brockmann.supplementary.caption,
                        color = Theme.v2.colors.text.tertiary,
                    )

                    UiSpacer(12.dp)

                    VsTextInputField(
                        textFieldState = operatorFeeFieldState,
                        hint = "0",
                        modifier = Modifier.fillMaxWidth(),
                    )

                    UiSpacer(12.dp)
                }
            }

            if (
                state.defiType == DeFiNavActions.REDEEM_YRUNE ||
                    state.defiType == DeFiNavActions.REDEEM_YTCY
            ) {
                Column(modifier = Modifier.padding(vertical = 2.dp)) {
                    Text(
                        text = stringResource(R.string.deposit_form_operator_slippage_title),
                        style = Theme.brockmann.supplementary.caption,
                        color = Theme.v2.colors.text.tertiary,
                    )

                    UiSpacer(12.dp)

                    VsTextInputField(
                        textFieldState = slippageTextFieldState,
                        hint = stringResource(R.string.slippage_hint),
                        modifier = Modifier.fillMaxWidth(),
                    )

                    UiSpacer(12.dp)
                }
            }

            val autoCompoundCopy =
                when (state.defiType) {
                    DeFiNavActions.STAKE_TCY,
                    DeFiNavActions.STAKE_STCY -> {
                        R.string.tcy_auto_compound_enable_title to
                            R.string.tcy_auto_compound_enable_subtitle
                    }
                    DeFiNavActions.UNSTAKE_TCY,
                    DeFiNavActions.UNSTAKE_STCY -> {
                        R.string.tcy_auto_compound_unstake_title to
                            R.string.tcy_auto_compound_unstake_subtitle
                    }
                    else -> null
                }

            autoCompoundCopy?.let { (titleRes, subtitleRes) ->
                AutoCompoundToggle(
                    title = stringResource(titleRes),
                    subtitle = stringResource(subtitleRes),
                    isChecked = state.isAutocompound,
                    onCheckedChange = onAutoCompoundCheckedChange,
                )
            }

            if (state.showGasFee && !isCircleMode) {
                FadingHorizontalDivider(modifier = Modifier.fillMaxWidth())

                UiSpacer(12.dp)

                EstimatedNetworkFee(
                    tokenGas = state.totalGas.asString(),
                    fiatGas = state.estimatedFee.asString(),
                )
            }
        }
    }
}

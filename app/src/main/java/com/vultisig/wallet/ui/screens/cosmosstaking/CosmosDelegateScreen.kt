package com.vultisig.wallet.ui.screens.cosmosstaking

import androidx.compose.foundation.background
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vultisig.wallet.R
import com.vultisig.wallet.data.blockchain.cosmos.staking.CosmosValidator
import com.vultisig.wallet.ui.components.PercentageChip
import com.vultisig.wallet.ui.components.TokenAmountInput
import com.vultisig.wallet.ui.components.UiIcon
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.buttons.VsButton
import com.vultisig.wallet.ui.components.buttons.VsButtonState
import com.vultisig.wallet.ui.components.buttons.VsButtonVariant
import com.vultisig.wallet.ui.components.v2.buttons.VsCircleButton
import com.vultisig.wallet.ui.components.v2.buttons.VsCircleButtonSize
import com.vultisig.wallet.ui.components.v2.buttons.VsCircleButtonType
import com.vultisig.wallet.ui.components.v2.scaffold.V2Scaffold
import com.vultisig.wallet.ui.models.cosmosstaking.CosmosDelegateUiState
import com.vultisig.wallet.ui.models.cosmosstaking.CosmosDelegateViewModel
import com.vultisig.wallet.ui.theme.Theme
import java.math.BigDecimal

/**
 * Stake form for LUNA / LUNC — mirrors iOS `CosmosDelegateTransactionScreen`: centered amount
 * input, 25/50/75/Max chips, "Balance available", and a validator picker row that opens the
 * [ValidatorPickerSheet]. On Continue the VM builds the `signDirect` keysign payload and routes to
 * the verify screen.
 */
@Composable
internal fun CosmosDelegateScreen(viewModel: CosmosDelegateViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()

    V2Scaffold(
        title =
            stringResource(
                R.string.cosmos_staking_delegate_title,
                state.ticker.ifEmpty { "Token" },
            ),
        onBackClick = viewModel::back,
    ) {
        DelegateContent(
            state = state,
            amountFieldState = viewModel.amountFieldState,
            onPercentage = viewModel::onPercentageChange,
            onPickValidator = viewModel::openValidatorPicker,
            onSubmit = viewModel::submit,
        )

        if (state.isShowingPicker) {
            ValidatorPickerSheet(
                title = stringResource(R.string.cosmos_staking_select_validator),
                searchQuery = state.validatorSearchQuery,
                onSearchQueryChange = viewModel::onSearchQueryChange,
                isLoading = state.isLoadingValidators,
                validators = viewModel.visibleValidators(state),
                ticker = state.ticker,
                decimal = state.decimal,
                selectedValidatorAddress = state.selectedValidator?.operatorAddress,
                onValidatorSelected = viewModel::selectValidator,
                onResolveAvatar = viewModel::resolveValidatorAvatar,
                onDismiss = viewModel::closeValidatorPicker,
            )
        }
    }
}

@Composable
private fun DelegateContent(
    state: CosmosDelegateUiState,
    amountFieldState: TextFieldState,
    onPercentage: (Int) -> Unit,
    onPickValidator: () -> Unit,
    onSubmit: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            // fillMaxSize + the weighted amount card make the form fill the screen (Figma); the
            // bottom reservation keeps the validator row clear of the pinned Continue button.
            modifier = Modifier.fillMaxSize().padding(16.dp).padding(bottom = 80.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            StakingAmountCard(
                ticker = state.ticker,
                amountFieldState = amountFieldState,
                available = state.stakeableBalance,
                percentageSelected = state.percentageSelected,
                onPercentage = onPercentage,
                modifier = Modifier.weight(1f),
            )

            ValidatorPickerField(selected = state.selectedValidator, onClick = onPickValidator)

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
            onClick = onSubmit,
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(16.dp),
        )
    }
}

/**
 * Centered amount input + 25/50/75/Max chips + balance-available row, in a bordered card that fills
 * the available height (Figma "Stake LUNA"): the "Amount" label pins to the top, the amount is
 * vertically centered in the slack, and the chips + balance pill pin to the bottom — mirroring the
 * SendFormScreen amount section. Shared by the delegate / redelegate forms. Pass
 * `Modifier.weight(1f)` so the card expands to fill the screen.
 */
@Composable
internal fun StakingAmountCard(
    ticker: String,
    amountFieldState: TextFieldState,
    available: BigDecimal,
    percentageSelected: Int,
    onPercentage: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val percentages = listOf(25, 50, 75, 100)
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .border(
                    width = 1.dp,
                    color = Theme.v2.colors.border.normal,
                    shape = RoundedCornerShape(12.dp),
                )
                .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(R.string.cosmos_staking_amount),
            style = Theme.brockmann.body.s.medium,
            color = Theme.v2.colors.text.primary,
        )
        // Amount vertically centered in the card's slack (SendFormScreen pattern).
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            TokenAmountInput(
                primaryFieldState = amountFieldState,
                primaryLabel = ticker.ifEmpty { "Token" },
                secondaryText = "",
                maxBalance = available.takeIf { it > BigDecimal.ZERO },
                modifier = Modifier.fillMaxWidth().align(Alignment.Center),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            percentages.forEach { percent ->
                PercentageChip(
                    title = if (percent == 100) stringResource(R.string.max) else "$percent%",
                    isSelected = percentageSelected == percent,
                    onClick = { onPercentage(percent) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
        BalanceAvailableRow(ticker = ticker, available = available)
    }
}

/** Filled "Balance available …" pill, matching the SendFormScreen amount section. */
@Composable
internal fun BalanceAvailableRow(ticker: String, available: BigDecimal) {
    Row(
        modifier =
            Modifier.fillMaxWidth()
                .background(
                    color = Theme.v2.colors.backgrounds.secondary,
                    shape = RoundedCornerShape(12.dp),
                )
                .padding(all = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = stringResource(R.string.send_form_balance_available),
            style = Theme.brockmann.body.s.medium,
            color = Theme.v2.colors.text.primary,
        )
        Text(
            text = "${available.stripTrailingZeros().toPlainString()} $ticker",
            style = Theme.brockmann.body.s.medium,
            color = Theme.v2.colors.text.secondary,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f),
        )
    }
}

/**
 * "Validator" picker opener row. Once a validator is picked it shows the avatar + moniker on the
 * left and a confirmed-check + edit affordance on the right (Figma "Stake LUNA"); before selection
 * it shows the "Validator" label and a chevron. The whole row re-opens the picker.
 */
@Composable
internal fun ValidatorPickerField(selected: CosmosValidator?, onClick: () -> Unit) {
    Row(
        modifier =
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .border(
                    width = 1.dp,
                    color = Theme.v2.colors.border.normal,
                    shape = RoundedCornerShape(12.dp),
                )
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.cosmos_staking_validator_picker),
                style = Theme.brockmann.body.s.medium,
                color = Theme.v2.colors.text.primary,
            )
            if (selected != null) {
                UiSpacer(size = 12.dp)
                ValidatorAvatar(
                    avatarUrl = null,
                    monogram =
                        selected.moniker.ifEmpty { selected.operatorAddress }.take(1).uppercase(),
                    size = 24.dp,
                    colorKey = selected.operatorAddress,
                )
                UiSpacer(size = 8.dp)
                Text(
                    text = selected.moniker.ifEmpty { truncated(selected.operatorAddress) },
                    style = Theme.brockmann.body.s.medium,
                    color = Theme.v2.colors.text.secondary,
                    maxLines = 1,
                )
            }
        }
        if (selected != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                UiIcon(
                    drawableResId = R.drawable.check,
                    size = 20.dp,
                    tint = Theme.v2.colors.alerts.success,
                )
                UiSpacer(size = 12.dp)
                UiIcon(
                    drawableResId = R.drawable.ic_edit_pencil,
                    size = 18.dp,
                    tint = Theme.v2.colors.text.secondary,
                    onClick = onClick,
                )
            }
        } else {
            Text(
                text = "›",
                style = Theme.brockmann.body.s.medium,
                color = Theme.v2.colors.text.secondary,
            )
        }
    }
}

/**
 * Searchable validator picker bottom sheet — mirrors iOS `ValidatorSelectionScreen`: each row shows
 * the moniker, voting power (total staked), and commission %, sorted by voting power desc.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ValidatorPickerSheet(
    title: String,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    isLoading: Boolean,
    validators: List<CosmosValidator>,
    ticker: String,
    decimal: Int,
    selectedValidatorAddress: String?,
    onValidatorSelected: (CosmosValidator) -> Unit,
    onResolveAvatar: suspend (String?) -> String?,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    // Two-tap staging (iOS parity): tapping a row stages the pick locally; the trailing ✓ commits
    // it (which closes the sheet) and the leading ✕ dismisses without committing.
    var pickedAddress by remember { mutableStateOf(selectedValidatorAddress) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Theme.v2.colors.backgrounds.primary,
    ) {
        // Full-screen sheet (Figma node 75918:74747): the column fills the height so the list
        // expands instead of leaving the stake screen visible above a ~¾-height sheet.
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            ValidatorPickerHeader(
                title = title,
                onClose = onDismiss,
                onConfirm = {
                    validators
                        .firstOrNull { it.operatorAddress == pickedAddress }
                        ?.let(onValidatorSelected)
                },
            )

            UiSpacer(size = 12.dp)

            // Figma: pill-shaped search with a leading magnifier (node 75918:74796).
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier =
                    Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(99.dp))
                        .background(Theme.v2.colors.backgrounds.surface1)
                        .border(
                            width = 1.dp,
                            color = Theme.v2.colors.border.normal,
                            shape = RoundedCornerShape(99.dp),
                        )
                        .padding(horizontal = 12.dp, vertical = 12.dp),
            ) {
                UiIcon(
                    drawableResId = R.drawable.ic_search,
                    size = 16.dp,
                    tint = Theme.v2.colors.text.tertiary,
                )
                BasicTextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChange,
                    singleLine = true,
                    textStyle = TextStyle(color = Theme.v2.colors.text.primary, fontSize = 16.sp),
                    decorationBox = { inner ->
                        if (searchQuery.isEmpty()) {
                            Text(
                                text = stringResource(R.string.token_selection_search_hint),
                                style = Theme.brockmann.supplementary.footnote,
                                color = Theme.v2.colors.text.tertiary,
                            )
                        }
                        inner()
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            UiSpacer(size = 16.dp)

            // Figma: column header clarifies the bare right-hand % is the validator's commission
            // rate (node 76925:75467), not voting power.
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = stringResource(R.string.cosmos_staking_validator_picker),
                    style = Theme.brockmann.supplementary.footnote,
                    color = Theme.v2.colors.text.tertiary,
                )
                Text(
                    text = stringResource(R.string.cosmos_staking_validator_commission),
                    style = Theme.brockmann.supplementary.footnote,
                    color = Theme.v2.colors.text.tertiary,
                )
            }

            UiSpacer(size = 8.dp)

            Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                when {
                    isLoading ->
                        Text(
                            text = stringResource(R.string.cosmos_staking_loading_validators),
                            style = Theme.brockmann.body.s.medium,
                            color = Theme.v2.colors.text.secondary,
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                        )
                    validators.isEmpty() ->
                        Text(
                            text = stringResource(R.string.cosmos_staking_no_validators_found),
                            style = Theme.brockmann.body.s.medium,
                            color = Theme.v2.colors.text.secondary,
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                        )
                    else ->
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            items(validators, key = { it.operatorAddress }) { validator ->
                                ValidatorPickerRow(
                                    validator = validator,
                                    ticker = ticker,
                                    decimal = decimal,
                                    isSelected = validator.operatorAddress == pickedAddress,
                                    onResolveAvatar = onResolveAvatar,
                                    onClick = { pickedAddress = validator.operatorAddress },
                                )
                            }
                        }
                }
            }

            UiSpacer(size = 16.dp)
        }
    }
}

/**
 * ✕ (dismiss) / title / ✓ (confirm) header for the validator picker. Uses the same [VsCircleButton]
 * chrome as the "Select Positions" sheet (tertiary close + primary tick).
 */
@Composable
private fun ValidatorPickerHeader(title: String, onClose: () -> Unit, onConfirm: () -> Unit) {
    // Figma: ✕ / ✓ on one row, then the large title left-aligned on its own line beneath them.
    Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            VsCircleButton(
                drawableResId = R.drawable.big_close,
                onClick = onClose,
                type = VsCircleButtonType.Tertiary,
                size = VsCircleButtonSize.Small,
            )
            VsCircleButton(
                drawableResId = R.drawable.big_tick,
                onClick = onConfirm,
                size = VsCircleButtonSize.Small,
            )
        }
        UiSpacer(size = 16.dp)
        Text(
            text = title,
            style = Theme.brockmann.headings.title3,
            color = Theme.v2.colors.text.primary,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun ValidatorPickerRow(
    validator: CosmosValidator,
    ticker: String,
    decimal: Int,
    isSelected: Boolean,
    onResolveAvatar: suspend (String?) -> String?,
    onClick: () -> Unit,
) {
    val avatarUrl by
        produceState<String?>(initialValue = null, key1 = validator.operatorAddress) {
            value = onResolveAvatar(validator.identity)
        }
    // Figma (node 75918:75259 / 75918:75266): spaced rounded-16 cards on surface-1. The selected
    // card carries a success-tinted fill + border; unselected cards are borderless.
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier =
            Modifier.fillMaxWidth()
                .clip(shape)
                .background(
                    if (isSelected) Theme.v2.colors.backgrounds.success
                    else Theme.v2.colors.backgrounds.surface1
                )
                .then(
                    if (isSelected)
                        Modifier.border(
                            width = 1.dp,
                            color = Theme.v2.colors.alerts.success,
                            shape = shape,
                        )
                    else Modifier
                )
                .clickable(onClick = onClick)
                .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ValidatorAvatar(
            avatarUrl = avatarUrl,
            monogram = validator.moniker.ifEmpty { validator.operatorAddress }.take(1).uppercase(),
            size = 36.dp,
            colorKey = validator.operatorAddress,
        )
        UiSpacer(size = 12.dp)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = validator.moniker.ifEmpty { validator.operatorAddress },
                style = Theme.brockmann.body.s.medium,
                color = Theme.v2.colors.text.primary,
                maxLines = 1,
            )
            Text(
                text = "${formatVotingPower(validator.votingPower, decimal)} $ticker",
                style = Theme.brockmann.supplementary.caption,
                color = Theme.v2.colors.text.secondary,
                maxLines = 1,
            )
        }
        UiSpacer(size = 8.dp)
        // Selected row carries a green confirmed-check to the left of the commission (Figma).
        if (isSelected) {
            UiIcon(
                drawableResId = R.drawable.check,
                size = 18.dp,
                tint = Theme.v2.colors.alerts.success,
            )
            UiSpacer(size = 8.dp)
        }
        Text(
            text = "${formatCommissionPercent(validator.commission)}%",
            style = Theme.brockmann.body.s.medium,
            color = Theme.v2.colors.text.primary,
            maxLines = 1,
        )
    }
}

/**
 * Commission arrives as a fraction (`0.0456…` = 4.56%). Render as a percentage with at most two
 * fraction digits, trailing zeros stripped — matches iOS `ValidatorCard.commissionText` (e.g.
 * "20%", "4.56%") instead of dumping the raw 18-decimal `cosmos.Dec`.
 */
private fun formatCommissionPercent(commission: BigDecimal): String =
    commission
        .movePointRight(2)
        .setScale(2, java.math.RoundingMode.HALF_UP)
        .stripTrailingZeros()
        .toPlainString()

/**
 * Voting power arrives as bond-denom base units (uint string). Render it in whole-token units with
 * thousands separators, matching the iOS validator picker (e.g. "137,888,608,306 LUNC"). The
 * base-unit decimals are chain-specific — LUNA/LUNC use 6, QBTC uses 8 — so the coin's [decimals]
 * must be passed in rather than assumed.
 */
private fun formatVotingPower(votingPowerBaseUnits: BigDecimal, decimals: Int): String {
    val whole = votingPowerBaseUnits.movePointLeft(decimals).toBigInteger()
    return "%,d".format(whole)
}

@Preview
@Composable
private fun CosmosDelegateScreenPreview() {
    V2Scaffold(title = "Stake LUNA", onBackClick = {}) {
        DelegateContent(
            state =
                CosmosDelegateUiState(
                    ticker = "LUNA",
                    stakeableBalance = BigDecimal("12.482"),
                    percentageSelected = 50,
                    selectedValidator =
                        CosmosValidator(
                            operatorAddress = "terravaloper1allnodes78wk",
                            moniker = "Allnodes",
                            commission = BigDecimal("0.05"),
                            jailed = false,
                            status = CosmosValidator.Status.Bonded,
                            votingPower = BigDecimal.ZERO,
                        ),
                ),
            amountFieldState = rememberTextFieldState("6.24"),
            onPercentage = {},
            onPickValidator = {},
            onSubmit = {},
        )
    }
}

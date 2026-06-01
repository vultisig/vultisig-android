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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.input.TextFieldState
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vultisig.wallet.R
import com.vultisig.wallet.data.blockchain.cosmos.staking.CosmosValidator
import com.vultisig.wallet.ui.components.PercentageChip
import com.vultisig.wallet.ui.components.TokenAmountInput
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
            stringResource(R.string.cosmos_staking_delegate_title, state.ticker.ifEmpty { "Token" })
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
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            StakingAmountCard(
                ticker = state.ticker,
                amountFieldState = amountFieldState,
                available = state.stakeableBalance,
                percentageSelected = state.percentageSelected,
                onPercentage = onPercentage,
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
 * Centered amount input + 25/50/75/Max chips + balance-available row, in a bordered card. Shared by
 * the delegate / undelegate / redelegate forms — `available` is the stakeable balance (delegate) or
 * the staked balance at the source validator (undelegate / redelegate).
 */
@Composable
internal fun StakingAmountCard(
    ticker: String,
    amountFieldState: TextFieldState,
    available: BigDecimal,
    percentageSelected: Int,
    onPercentage: (Int) -> Unit,
) {
    val percentages = listOf(25, 50, 75, 100)
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
        Text(
            text = stringResource(R.string.cosmos_staking_amount),
            style = Theme.brockmann.body.s.medium,
            color = Theme.v2.colors.text.primary,
        )
        TokenAmountInput(
            primaryFieldState = amountFieldState,
            primaryLabel = ticker.ifEmpty { "Token" },
            secondaryText = "",
            maxBalance = available.takeIf { it > BigDecimal.ZERO },
            modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
        )
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
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                text = stringResource(R.string.send_form_balance_available),
                style = Theme.brockmann.body.s.medium,
                color = Theme.v2.colors.text.secondary,
            )
            Text(
                text = "${available.stripTrailingZeros().toPlainString()} $ticker",
                style = Theme.brockmann.body.s.medium,
                color = Theme.v2.colors.text.primary,
            )
        }
    }
}

/** "Validator >" picker opener row — shows the selected validator's moniker when picked. */
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
        Text(
            text =
                selected?.moniker?.ifEmpty { selected.operatorAddress }
                    ?: stringResource(R.string.cosmos_staking_validator_picker),
            style = Theme.brockmann.body.s.medium,
            color =
                if (selected != null) Theme.v2.colors.text.primary
                else Theme.v2.colors.text.secondary,
        )
        Text(
            text = "›",
            style = Theme.brockmann.body.s.medium,
            color = Theme.v2.colors.text.secondary,
        )
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
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
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

            BasicTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                singleLine = true,
                textStyle = TextStyle(color = Theme.v2.colors.text.primary, fontSize = 16.sp),
                decorationBox = { inner ->
                    if (searchQuery.isEmpty()) {
                        Text(
                            text = stringResource(R.string.token_selection_search_hint),
                            style = Theme.brockmann.body.s.medium,
                            color = Theme.v2.colors.text.secondary,
                        )
                    }
                    inner()
                },
                modifier =
                    Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Theme.v2.colors.backgrounds.secondary)
                        .padding(horizontal = 16.dp, vertical = 14.dp),
            )

            UiSpacer(size = 12.dp)

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
                    LazyColumn(modifier = Modifier.fillMaxWidth().height(440.dp)) {
                        items(validators, key = { it.operatorAddress }) { validator ->
                            ValidatorPickerRow(
                                validator = validator,
                                ticker = ticker,
                                isSelected = validator.operatorAddress == pickedAddress,
                                onResolveAvatar = onResolveAvatar,
                                onClick = { pickedAddress = validator.operatorAddress },
                            )
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
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        VsCircleButton(
            drawableResId = R.drawable.big_close,
            onClick = onClose,
            type = VsCircleButtonType.Tertiary,
            size = VsCircleButtonSize.Small,
        )
        Text(
            text = title,
            style = Theme.brockmann.headings.title3,
            color = Theme.v2.colors.text.primary,
        )
        VsCircleButton(
            drawableResId = R.drawable.big_tick,
            onClick = onConfirm,
            size = VsCircleButtonSize.Small,
        )
    }
}

@Composable
private fun ValidatorPickerRow(
    validator: CosmosValidator,
    ticker: String,
    isSelected: Boolean,
    onResolveAvatar: suspend (String?) -> String?,
    onClick: () -> Unit,
) {
    val avatarUrl by
        produceState<String?>(initialValue = null, key1 = validator.operatorAddress) {
            value = onResolveAvatar(validator.identity)
        }
    Row(
        modifier =
            Modifier.fillMaxWidth()
                .padding(vertical = 4.dp)
                .clip(RoundedCornerShape(12.dp))
                .border(
                    width = if (isSelected) 2.dp else 1.dp,
                    color =
                        if (isSelected) Theme.v2.colors.primary.accent4
                        else Theme.v2.colors.border.normal,
                    shape = RoundedCornerShape(12.dp),
                )
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ValidatorAvatar(
            avatarUrl = avatarUrl,
            monogram = validator.moniker.ifEmpty { validator.operatorAddress }.take(1).uppercase(),
            size = 36.dp,
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
                text = "${formatVotingPower(validator.votingPower, ticker)} $ticker",
                style = Theme.brockmann.supplementary.caption,
                color = Theme.v2.colors.text.secondary,
                maxLines = 1,
            )
        }
        UiSpacer(size = 8.dp)
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
 * thousands separators, matching the iOS validator picker (e.g. "137,888,608,306 LUNC"). LUNA/LUNC
 * use 6 decimals.
 */
private fun formatVotingPower(votingPowerBaseUnits: BigDecimal, ticker: String): String {
    val decimals = if (ticker == "LUNA" || ticker == "LUNC") 6 else 6
    val whole = votingPowerBaseUnits.movePointLeft(decimals).toBigInteger()
    return "%,d".format(whole)
}

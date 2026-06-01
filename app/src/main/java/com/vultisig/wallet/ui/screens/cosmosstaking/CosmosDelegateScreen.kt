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
            AmountCard(
                state = state,
                amountFieldState = amountFieldState,
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

/** Centered amount input + 25/50/75/Max chips + balance-available row, in a bordered card. */
@Composable
internal fun AmountCard(
    state: CosmosDelegateUiState,
    amountFieldState: TextFieldState,
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
            primaryLabel = state.ticker.ifEmpty { "Token" },
            secondaryText = "",
            maxBalance = state.stakeableBalance.takeIf { it > BigDecimal.ZERO },
            modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            percentages.forEach { percent ->
                PercentageChip(
                    title = if (percent == 100) stringResource(R.string.max) else "$percent%",
                    isSelected = state.percentageSelected == percent,
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
                text =
                    "${state.stakeableBalance.stripTrailingZeros().toPlainString()} ${state.ticker}",
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
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Theme.v2.colors.backgrounds.primary,
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            Text(
                text = title,
                style = Theme.brockmann.headings.title3,
                color = Theme.v2.colors.text.primary,
            )

            UiSpacer(size = 12.dp)

            BasicTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                singleLine = true,
                textStyle = TextStyle(color = Theme.v2.colors.text.primary, fontSize = 16.sp),
                modifier =
                    Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Theme.v2.colors.backgrounds.secondary)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
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
                                isSelected = validator.operatorAddress == selectedValidatorAddress,
                                onClick = { onValidatorSelected(validator) },
                            )
                        }
                    }
            }

            UiSpacer(size = 16.dp)
        }
    }
}

@Composable
private fun ValidatorPickerRow(
    validator: CosmosValidator,
    ticker: String,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
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
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = validator.moniker.ifEmpty { validator.operatorAddress },
                style = Theme.brockmann.body.s.medium,
                color = Theme.v2.colors.text.primary,
            )
            Text(
                text = "${formatVotingPower(validator.votingPower, ticker)} $ticker",
                style = Theme.brockmann.supplementary.caption,
                color = Theme.v2.colors.text.secondary,
            )
        }
        Text(
            text =
                "${validator.commission.movePointRight(2).stripTrailingZeros().toPlainString()}%",
            style = Theme.brockmann.body.s.medium,
            color = Theme.v2.colors.text.secondary,
        )
    }
}

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

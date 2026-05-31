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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vultisig.wallet.R
import com.vultisig.wallet.data.blockchain.cosmos.staking.CosmosValidator
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.buttons.VsButton
import com.vultisig.wallet.ui.components.buttons.VsButtonState
import com.vultisig.wallet.ui.components.buttons.VsButtonVariant
import com.vultisig.wallet.ui.components.v2.scaffold.V2Scaffold
import com.vultisig.wallet.ui.models.cosmosstaking.CosmosDelegateUiState
import com.vultisig.wallet.ui.models.cosmosstaking.CosmosDelegateViewModel
import com.vultisig.wallet.ui.theme.Theme

/**
 * Form screen for LUNA / LUNC delegate. Inputs: amount + validator (picked through a modal sheet).
 *
 * Polish (matching the iOS Figma exactly) lives in later sessions:
 * - Headroom-aware stakeable balance display + 25/50/75/Max chips
 * - Fiat toggle
 * - Validator card details (commission, voting power %, Keybase avatar)
 * - Inline validation errors per field
 *
 * The MVP here proves the end-to-end path compiles + routes correctly into the existing
 * verify-deposit / keysign pipeline.
 */
@Composable
internal fun CosmosDelegateScreen(viewModel: CosmosDelegateViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()

    V2Scaffold(title = "Stake ${state.ticker.ifEmpty { "Token" }}") {
        DelegateContent(
            state = state,
            amountText = viewModel.amountFieldState.text.toString(),
            onAmountChange = { newText ->
                viewModel.amountFieldState.edit { replace(0, length, newText) }
            },
            onPickValidator = viewModel::openValidatorPicker,
            onSubmit = viewModel::submit,
        )

        if (state.isShowingPicker) {
            ValidatorPickerSheet(
                searchQuery = state.validatorSearchQuery,
                onSearchQueryChange = viewModel::onSearchQueryChange,
                isLoading = state.isLoadingValidators,
                validators = viewModel.visibleValidators(state),
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
    amountText: String,
    onAmountChange: (String) -> Unit,
    onPickValidator: () -> Unit,
    onSubmit: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionLabel(label = "Amount (${state.ticker.ifEmpty { "Token" }})")
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

            SectionLabel(label = "Validator")
            ValidatorRow(selected = state.selectedValidator, onClick = onPickValidator)

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
            label = stringResourceOrFallback(R.string.send_continue_button, "Continue"),
            variant = VsButtonVariant.CTA,
            state = if (state.isSubmitting) VsButtonState.Disabled else VsButtonState.Enabled,
            onClick = onSubmit,
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(16.dp),
        )
    }
}

@Composable
private fun SectionLabel(label: String) {
    Text(text = label, style = Theme.brockmann.body.s.medium, color = Theme.v2.colors.text.primary)
}

@Composable
private fun ValidatorRow(selected: CosmosValidator?, onClick: () -> Unit) {
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
                .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = selected?.moniker?.ifEmpty { selected.operatorAddress } ?: "Select a validator",
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ValidatorPickerSheet(
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    isLoading: Boolean,
    validators: List<CosmosValidator>,
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
                text = "Pick a validator",
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

            if (isLoading) {
                Text(
                    text = "Loading validators…",
                    style = Theme.brockmann.body.s.medium,
                    color = Theme.v2.colors.text.secondary,
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                )
            } else if (validators.isEmpty()) {
                Text(
                    text = "No validators match your search",
                    style = Theme.brockmann.body.s.medium,
                    color = Theme.v2.colors.text.secondary,
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                )
            } else {
                LazyColumn(modifier = Modifier.fillMaxWidth().height(420.dp)) {
                    items(validators, key = { it.operatorAddress }) { validator ->
                        ValidatorPickerRow(
                            validator = validator,
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
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier.fillMaxWidth()
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
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = validator.moniker.ifEmpty { validator.operatorAddress },
                style = Theme.brockmann.body.s.medium,
                color = Theme.v2.colors.text.primary,
            )
            Text(
                text = "Commission ${(validator.commission.movePointRight(2)).toPlainString()}%",
                style = Theme.brockmann.supplementary.caption,
                color = Theme.v2.colors.text.secondary,
            )
        }
    }
}

@Composable
private fun stringResourceOrFallback(id: Int, fallback: String): String =
    runCatching { androidx.compose.ui.res.stringResource(id) }.getOrDefault(fallback)

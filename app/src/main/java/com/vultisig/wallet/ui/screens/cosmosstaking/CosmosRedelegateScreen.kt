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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vultisig.wallet.data.blockchain.cosmos.staking.CosmosRedelegationCooldownState
import com.vultisig.wallet.data.blockchain.cosmos.staking.CosmosValidator
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.buttons.VsButton
import com.vultisig.wallet.ui.components.buttons.VsButtonState
import com.vultisig.wallet.ui.components.buttons.VsButtonVariant
import com.vultisig.wallet.ui.components.v2.scaffold.V2Scaffold
import com.vultisig.wallet.ui.models.cosmosstaking.CosmosRedelegateViewModel
import com.vultisig.wallet.ui.theme.Theme

/**
 * Redelegate input form for LUNA / LUNC. Amount first, then destination picker via a modal-bottom-
 * sheet (source excluded). If the source is under a 21-day redelegation cooldown, the cooldown
 * notice replaces the form and Continue is disabled.
 *
 * Mirrors iOS `CosmosRedelegateTransactionScreen.swift` (vultisig-ios PR #4432).
 */
@Composable
internal fun CosmosRedelegateScreen(viewModel: CosmosRedelegateViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    V2Scaffold(title = "Move ${state.ticker.ifEmpty { "Token" }}") {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (state.cooldownState is CosmosRedelegationCooldownState.Blocked) {
                    UnbondingLockNotice(
                        message =
                            state.cooldownBlockedMessage
                                ?: "Source validator is under a 21-day redelegation cooldown"
                    )
                } else {
                    ValidatorReadonlyBlock(
                        moniker = state.srcValidatorMoniker,
                        address = state.srcValidatorAddress,
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

                    Text(
                        text = "Destination validator",
                        style = Theme.brockmann.body.s.medium,
                        color = Theme.v2.colors.text.primary,
                    )
                    DstValidatorPickerRow(
                        selected = state.selectedDstValidator,
                        onClick = viewModel::openValidatorPicker,
                    )
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
                state =
                    if (
                        state.isSubmitting ||
                            state.cooldownState is CosmosRedelegationCooldownState.Blocked
                    )
                        VsButtonState.Disabled
                    else VsButtonState.Enabled,
                onClick = viewModel::submit,
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(16.dp),
            )
        }

        if (state.isShowingPicker) {
            DstValidatorPickerSheet(
                searchQuery = state.validatorSearchQuery,
                onSearchQueryChange = viewModel::onSearchQueryChange,
                isLoading = state.isLoadingValidators,
                validators = viewModel.visibleValidators(state),
                selectedValidatorAddress = state.selectedDstValidator?.operatorAddress,
                onValidatorSelected = viewModel::selectDstValidator,
                onDismiss = viewModel::closeValidatorPicker,
            )
        }
    }
}

@Composable
private fun DstValidatorPickerRow(selected: CosmosValidator?, onClick: () -> Unit) {
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
            text =
                selected?.moniker?.ifEmpty { selected.operatorAddress }
                    ?: "Pick a destination validator",
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
private fun DstValidatorPickerSheet(
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
                text = "Pick destination",
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
                        text = "Loading validators…",
                        style = Theme.brockmann.body.s.medium,
                        color = Theme.v2.colors.text.secondary,
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                    )
                validators.isEmpty() ->
                    Text(
                        text = "No validators match your search",
                        style = Theme.brockmann.body.s.medium,
                        color = Theme.v2.colors.text.secondary,
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                    )
                else ->
                    LazyColumn(modifier = Modifier.fillMaxWidth().height(420.dp)) {
                        items(validators, key = { it.operatorAddress }) { validator ->
                            Row(
                                modifier =
                                    Modifier.fillMaxWidth()
                                        .padding(vertical = 4.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .border(
                                            width =
                                                if (
                                                    validator.operatorAddress ==
                                                        selectedValidatorAddress
                                                )
                                                    2.dp
                                                else 1.dp,
                                            color =
                                                if (
                                                    validator.operatorAddress ==
                                                        selectedValidatorAddress
                                                )
                                                    Theme.v2.colors.primary.accent4
                                                else Theme.v2.colors.border.normal,
                                            shape = RoundedCornerShape(12.dp),
                                        )
                                        .clickable { onValidatorSelected(validator) }
                                        .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    Text(
                                        text =
                                            validator.moniker.ifEmpty { validator.operatorAddress },
                                        style = Theme.brockmann.body.s.medium,
                                        color = Theme.v2.colors.text.primary,
                                    )
                                    Text(
                                        text =
                                            "Commission ${(validator.commission.movePointRight(2)).toPlainString()}%",
                                        style = Theme.brockmann.supplementary.caption,
                                        color = Theme.v2.colors.text.secondary,
                                    )
                                }
                            }
                        }
                    }
            }
            UiSpacer(size = 16.dp)
        }
    }
}

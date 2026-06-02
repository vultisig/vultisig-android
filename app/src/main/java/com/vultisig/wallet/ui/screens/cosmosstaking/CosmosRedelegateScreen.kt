package com.vultisig.wallet.ui.screens.cosmosstaking

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vultisig.wallet.R
import com.vultisig.wallet.data.blockchain.cosmos.staking.CosmosRedelegationCooldownState
import com.vultisig.wallet.data.blockchain.cosmos.staking.CosmosValidator
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
    V2Scaffold(
        title =
            stringResource(
                R.string.cosmos_staking_redelegate_title,
                state.ticker.ifEmpty { "Token" },
            )
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (state.cooldownState is CosmosRedelegationCooldownState.Blocked) {
                    UnbondingLockNotice(
                        message =
                            state.cooldownBlockedMessage
                                ?: stringResource(
                                    R.string.cosmos_staking_redelegate_cooldown_fallback
                                )
                    )
                } else {
                    ValidatorReadonlyBlock(
                        moniker = state.srcValidatorMoniker,
                        address = state.srcValidatorAddress,
                    )

                    StakingAmountCard(
                        ticker = state.ticker,
                        amountFieldState = viewModel.amountFieldState,
                        available = state.stakedBalance,
                        percentageSelected = state.percentageSelected,
                        onPercentage = viewModel::onPercentageChange,
                    )

                    Text(
                        text = stringResource(R.string.cosmos_staking_redelegate_source),
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
                label = stringResource(R.string.cosmos_staking_continue),
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
            ValidatorPickerSheet(
                title = stringResource(R.string.cosmos_staking_pick_destination),
                searchQuery = state.validatorSearchQuery,
                onSearchQueryChange = viewModel::onSearchQueryChange,
                isLoading = state.isLoadingValidators,
                validators = viewModel.visibleValidators(state),
                ticker = state.ticker,
                selectedValidatorAddress = state.selectedDstValidator?.operatorAddress,
                onValidatorSelected = viewModel::selectDstValidator,
                onResolveAvatar = viewModel::resolveValidatorAvatar,
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
                    ?: stringResource(R.string.cosmos_staking_pick_destination_validator),
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

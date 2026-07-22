package com.vultisig.wallet.ui.screens.v2.defi.solana

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
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.UiIcon
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.buttons.VsButton
import com.vultisig.wallet.ui.components.buttons.VsButtonState
import com.vultisig.wallet.ui.components.buttons.VsButtonVariant
import com.vultisig.wallet.ui.components.v2.scaffold.V2Scaffold
import com.vultisig.wallet.ui.models.solanastaking.SolanaDelegateUiState
import com.vultisig.wallet.ui.models.solanastaking.SolanaDelegateViewModel
import com.vultisig.wallet.ui.models.solanastaking.SolanaValidatorOption
import com.vultisig.wallet.ui.screens.cosmosstaking.StakingAmountCard
import com.vultisig.wallet.ui.screens.cosmosstaking.ValidatorAvatar
import com.vultisig.wallet.ui.theme.Theme
import com.vultisig.wallet.ui.utils.asString
import java.math.BigDecimal

/**
 * Solana delegate (stake) screen. Mirrors the Cosmos/TON delegate layout: a full-height amount card
 * with 25/50/75/Max chips + the live "Balance available" row, a validator picker row that opens the
 * [SolanaValidatorPickerSheet], and a pinned Continue button. On Continue the VM builds the
 * byte-parity keysign payload and routes to the verify screen.
 */
@Composable
internal fun SolanaDelegateScreen(viewModel: SolanaDelegateViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()

    V2Scaffold(
        title = stringResource(R.string.solana_delegate_title),
        onBackClick = viewModel::back,
    ) {
        SolanaDelegateContent(
            state = state,
            amountFieldState = viewModel.amountFieldState,
            onPercentage = viewModel::onPercentageChange,
            onPickValidator = viewModel::openValidatorPicker,
            onSubmit = viewModel::submit,
        )

        if (state.isShowingPicker) {
            SolanaValidatorPickerSheet(
                isLoading = state.isLoading,
                searchQuery = state.validatorSearchQuery,
                selectedVotePubkey = state.selectedValidator?.votePubkey,
                validators = viewModel.visibleValidators(state),
                onSearchQueryChange = viewModel::onSearchQueryChange,
                onValidatorSelected = viewModel::selectValidator,
                onDismiss = viewModel::closeValidatorPicker,
            )
        }
    }
}

@Composable
internal fun SolanaDelegateContent(
    state: SolanaDelegateUiState,
    amountFieldState: androidx.compose.foundation.text.input.TextFieldState,
    onPercentage: (Int) -> Unit,
    onPickValidator: () -> Unit,
    onSubmit: () -> Unit,
) {
    val amount = amountFieldState.text.toString().toBigDecimalOrNull()
    // Live validation matching iOS: over-balance first, then the 1 SOL minimum-delegation floor.
    val validationError: String? =
        when {
            amount == null -> null
            amount > state.stakeableBalance ->
                stringResource(R.string.solana_delegate_amount_exceeds_balance)
            amount < BigDecimal.ONE -> stringResource(R.string.solana_delegate_min_delegation)
            else -> null
        }
    val canContinue =
        state.selectedValidator != null &&
            amount != null &&
            validationError == null &&
            !state.isSubmitting

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
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

            (validationError ?: state.error?.asString())?.let { error ->
                Text(
                    text = error,
                    style = Theme.brockmann.supplementary.caption,
                    color = Theme.v2.colors.alerts.error,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                )
            }

            SolanaValidatorPickerField(
                selected = state.selectedValidator,
                onClick = onPickValidator,
            )
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

/** "Validator" opener row: label + chevron before selection; avatar + name after. */
@Composable
internal fun SolanaValidatorPickerField(selected: SolanaValidatorOption?, onClick: () -> Unit) {
    Row(
        modifier =
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .border(
                    width = 1.dp,
                    color = Theme.v2.colors.border.light,
                    shape = RoundedCornerShape(12.dp),
                )
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.solana_delegate_validator),
                style = Theme.brockmann.body.s.medium,
                color = Theme.v2.colors.text.primary,
            )
            if (selected != null) {
                UiSpacer(size = 12.dp)
                ValidatorAvatar(
                    avatarUrl = selected.logoUrl,
                    monogram = selected.name.take(1),
                    size = 24.dp,
                    colorKey = selected.votePubkey,
                )
                UiSpacer(size = 8.dp)
                Text(
                    text = selected.name,
                    style = Theme.brockmann.body.s.medium,
                    color = Theme.v2.colors.text.secondary,
                    maxLines = 1,
                )
            }
        }
        if (selected != null) {
            UiIcon(
                drawableResId = R.drawable.check,
                size = 20.dp,
                tint = Theme.v2.colors.alerts.success,
            )
        } else {
            Text(
                text = "›",
                style = Theme.brockmann.body.s.medium,
                color = Theme.v2.colors.text.secondary,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SolanaValidatorPickerSheet(
    isLoading: Boolean,
    searchQuery: String,
    selectedVotePubkey: String?,
    validators: List<SolanaValidatorOption>,
    onSearchQueryChange: (String) -> Unit,
    onValidatorSelected: (SolanaValidatorOption) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val searchState = rememberTextFieldState(searchQuery)
    LaunchedEffect(searchState) {
        snapshotFlow { searchState.text.toString() }.collect { onSearchQueryChange(it) }
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Theme.v2.colors.backgrounds.primary,
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            Text(
                text = stringResource(R.string.cosmos_staking_select_validator),
                style = Theme.brockmann.headings.title3,
                color = Theme.v2.colors.text.primary,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )

            UiSpacer(size = 12.dp)

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier =
                    Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(99.dp))
                        .background(Theme.v2.colors.backgrounds.surface1)
                        .border(
                            width = 1.dp,
                            color = Theme.v2.colors.border.light,
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
                    state = searchState,
                    lineLimits = TextFieldLineLimits.SingleLine,
                    textStyle = TextStyle(color = Theme.v2.colors.text.primary, fontSize = 16.sp),
                    decorator = { inner ->
                        if (searchState.text.isEmpty()) {
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

            Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                when {
                    isLoading ->
                        CenteredMessage(stringResource(R.string.cosmos_staking_loading_validators))
                    validators.isEmpty() ->
                        CenteredMessage(stringResource(R.string.solana_delegate_select_validator))
                    else ->
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            items(validators, key = { it.votePubkey }) { validator ->
                                SolanaValidatorRow(
                                    validator = validator,
                                    isSelected = validator.votePubkey == selectedVotePubkey,
                                    onClick = { onValidatorSelected(validator) },
                                )
                            }
                        }
                }
            }

            UiSpacer(size = 16.dp)
        }
    }
}

@Composable
private fun SolanaValidatorRow(
    validator: SolanaValidatorOption,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
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
                    if (isSelected) Modifier.border(1.dp, Theme.v2.colors.alerts.success, shape)
                    else Modifier
                )
                .clickable(onClick = onClick)
                .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ValidatorAvatar(
            avatarUrl = validator.logoUrl,
            monogram = validator.name.take(1),
            size = 36.dp,
            colorKey = validator.votePubkey,
        )
        UiSpacer(size = 12.dp)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = validator.name,
                style = Theme.brockmann.body.s.medium,
                color = Theme.v2.colors.text.primary,
                maxLines = 1,
            )
            Text(
                text = validator.activatedStakeDisplay,
                style = Theme.brockmann.supplementary.caption,
                color = Theme.v2.colors.text.tertiary,
                maxLines = 1,
            )
        }
        UiSpacer(size = 8.dp)
        Text(
            text = validator.commissionDisplay,
            style = Theme.brockmann.body.s.medium,
            color = Theme.v2.colors.text.primary,
            maxLines = 1,
        )
    }
}

@Composable
private fun CenteredMessage(text: String) {
    Text(
        text = text,
        style = Theme.brockmann.body.s.medium,
        color = Theme.v2.colors.text.secondary,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(24.dp),
    )
}

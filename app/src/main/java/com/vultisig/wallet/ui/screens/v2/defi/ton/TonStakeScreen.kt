package com.vultisig.wallet.ui.screens.v2.defi.ton

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import com.vultisig.wallet.ui.models.defi.TonPoolUiModel
import com.vultisig.wallet.ui.models.defi.TonStakeUiState
import com.vultisig.wallet.ui.models.defi.TonStakeViewModel
import com.vultisig.wallet.ui.screens.cosmosstaking.StakingAmountCard
import com.vultisig.wallet.ui.theme.Theme
import com.vultisig.wallet.ui.utils.asString
import java.math.BigDecimal
import java.text.DecimalFormat

/** Dedicated TON nominator-pool stake screen. Mirrors iOS `TonStakeTransactionScreen`. */
@Composable
internal fun TonStakeScreen(viewModel: TonStakeViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()

    V2Scaffold(
        title = stringResource(R.string.ton_stake_title, state.ticker.ifEmpty { "TON" }),
        onBackClick = viewModel::back,
    ) {
        val amountText = viewModel.amountFieldState.text.toString()
        val amount = amountText.toBigDecimalOrNull()
        val belowMinimum = amount == null || amount < state.requiredMinStake
        val canContinue = state.selectedPool != null && !belowMinimum && !state.isSubmitting

        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(16.dp).padding(bottom = 80.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                StakingAmountCard(
                    ticker = state.ticker.ifEmpty { "TON" },
                    amountFieldState = viewModel.amountFieldState,
                    available = state.stakeableBalance,
                    percentageSelected = state.percentageSelected,
                    onPercentage = viewModel::onPercentageChange,
                    modifier = Modifier.weight(1f),
                )

                // Dynamic minimum-stake hint (pool min + ~1 TON buffer), matching macOS.
                if (belowMinimum) {
                    Text(
                        text =
                            stringResource(
                                R.string.ton_stake_error_min_amount,
                                state.requiredMinStake.stripTrailingZeros().toPlainString(),
                            ),
                        style = Theme.brockmann.supplementary.caption,
                        color = Theme.v2.colors.alerts.error,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                    )
                }

                TonPoolPickerField(
                    selected = state.selectedPool,
                    onClick = viewModel::openPoolPicker,
                )

                state.errorMessage?.let { error ->
                    Text(
                        text = error.asString(),
                        style = Theme.brockmann.supplementary.caption,
                        color = Theme.v2.colors.alerts.error,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                    )
                }
            }

            VsButton(
                label = stringResource(R.string.cosmos_staking_continue),
                variant = VsButtonVariant.CTA,
                state = if (canContinue) VsButtonState.Enabled else VsButtonState.Disabled,
                isLoading = state.isSubmitting,
                onClick = viewModel::submit,
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(16.dp),
            )
        }

        if (state.isShowingPicker) {
            TonPoolPickerSheet(
                state = state,
                searchTextFieldState = viewModel.searchTextFieldState,
                onSearchQueryChange = viewModel::onSearchQueryChange,
                onPoolSelected = viewModel::onPoolSelected,
                onDismiss = viewModel::closePoolPicker,
            )
        }
    }
}

/** "Pool" opener row: label + chevron before selection; avatar + name + edit affordance after. */
@Composable
private fun TonPoolPickerField(selected: TonPoolUiModel?, onClick: () -> Unit) {
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
                text = stringResource(R.string.ton_staking_pool_header),
                style = Theme.brockmann.body.s.medium,
                color = Theme.v2.colors.text.primary,
            )
            if (selected != null) {
                UiSpacer(size = 12.dp)
                PoolMonogram(selected.displayName.take(1).uppercase(), size = 24)
                UiSpacer(size = 8.dp)
                Text(
                    text = selected.displayName,
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
private fun TonPoolPickerSheet(
    state: TonStakeUiState,
    searchTextFieldState: androidx.compose.foundation.text.input.TextFieldState,
    onSearchQueryChange: (String) -> Unit,
    onPoolSelected: (TonPoolUiModel) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Theme.v2.colors.backgrounds.primary,
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            Text(
                text = stringResource(R.string.ton_staking_select_pool),
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
                    value = searchTextFieldState.text.toString(),
                    onValueChange = onSearchQueryChange,
                    singleLine = true,
                    textStyle = TextStyle(color = Theme.v2.colors.text.primary, fontSize = 16.sp),
                    decorationBox = { inner ->
                        if (searchTextFieldState.text.isEmpty()) {
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

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = stringResource(R.string.ton_staking_pool_header),
                    style = Theme.brockmann.supplementary.footnote,
                    color = Theme.v2.colors.text.tertiary,
                )
                Text(
                    text = stringResource(R.string.ton_staking_apy_header),
                    style = Theme.brockmann.supplementary.footnote,
                    color = Theme.v2.colors.text.tertiary,
                )
            }

            UiSpacer(size = 8.dp)

            Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                when {
                    state.isLoadingPools ->
                        CenteredMessage(stringResource(R.string.cosmos_staking_loading_validators))
                    state.pools.isEmpty() ->
                        CenteredMessage(stringResource(R.string.ton_staking_no_pools_found))
                    else ->
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            items(state.pools, key = { it.address }) { pool ->
                                TonPoolRow(
                                    pool = pool,
                                    isSelected = pool.address == state.selectedPool?.address,
                                    onClick = { onPoolSelected(pool) },
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
private fun CenteredMessage(text: String) {
    Text(
        text = text,
        style = Theme.brockmann.body.s.medium,
        color = Theme.v2.colors.text.secondary,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(24.dp),
    )
}

@Composable
private fun TonPoolRow(pool: TonPoolUiModel, isSelected: Boolean, onClick: () -> Unit) {
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
        PoolMonogram(pool.displayName.take(1).uppercase(), size = 36)
        UiSpacer(size = 12.dp)
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = pool.displayName,
                    style = Theme.brockmann.body.s.medium,
                    color = Theme.v2.colors.text.primary,
                    maxLines = 1,
                )
                if (pool.verified) {
                    UiSpacer(size = 4.dp)
                    UiIcon(
                        drawableResId = R.drawable.check,
                        size = 14.dp,
                        tint = Theme.v2.colors.alerts.success,
                    )
                }
            }
            Text(
                text =
                    stringResource(R.string.ton_staking_min_stake, formatMinStake(pool.minStake)),
                style = Theme.brockmann.supplementary.caption,
                color = Theme.v2.colors.text.secondary,
                maxLines = 1,
            )
        }
        UiSpacer(size = 8.dp)
        Text(
            text = stringResource(R.string.ton_staking_apy_value, formatApy(pool.apy)),
            style = Theme.brockmann.body.s.medium,
            color = Theme.v2.colors.alerts.success,
            maxLines = 1,
        )
    }
}

@Composable
private fun PoolMonogram(letter: String, size: Int) {
    Box(
        modifier =
            Modifier.size(size.dp)
                .clip(CircleShape)
                .background(Theme.v2.colors.backgrounds.secondary),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = letter,
            style = Theme.brockmann.body.s.medium,
            color = Theme.v2.colors.text.primary,
        )
    }
}

private fun formatMinStake(minStake: BigDecimal): String =
    minStake.stripTrailingZeros().toPlainString()

private fun formatApy(apy: Double): String = DecimalFormat("0.##").format(apy)

package com.vultisig.wallet.ui.screens.cosmosstaking

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vultisig.wallet.R
import com.vultisig.wallet.data.blockchain.cosmos.staking.CosmosStakingConfig
import com.vultisig.wallet.ui.components.TokenAmountInput
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.buttons.VsButton
import com.vultisig.wallet.ui.components.buttons.VsButtonState
import com.vultisig.wallet.ui.components.buttons.VsButtonVariant
import com.vultisig.wallet.ui.components.v2.scaffold.V2Scaffold
import com.vultisig.wallet.ui.models.cosmosstaking.CosmosUndelegateViewModel
import com.vultisig.wallet.ui.theme.Theme
import java.math.BigDecimal
import kotlin.math.roundToInt

/**
 * Undelegate input form for LUNA / LUNC. Same shape as the iOS `CosmosUndelegateTransactionScreen`
 * minus the validator picker — the validator is pre-selected by the caller (from the position card)
 * and surfaced as read-only. The 21-day unbonding-lock notice is inline so the user accepts the
 * lock before confirming.
 */
@Composable
internal fun CosmosUndelegateScreen(viewModel: CosmosUndelegateViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    V2Scaffold(
        title =
            stringResource(
                R.string.cosmos_staking_undelegate_title,
                state.ticker.ifEmpty { stringResource(R.string.cosmos_staking_token_fallback) },
            ),
        onBackClick = viewModel::back,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(16.dp).padding(bottom = 80.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ValidatorReadonlyBlock(
                    moniker = state.validatorMoniker,
                    address = state.validatorAddress,
                )

                UnstakeAmountCard(
                    ticker = state.ticker,
                    amountFieldState = viewModel.amountFieldState,
                    available = state.stakedBalance,
                    percentageSelected = state.percentageSelected,
                    onPercentage = viewModel::onPercentageChange,
                    modifier = Modifier.weight(1f),
                )

                val unbondingMsg = state.unbondingLockMessage
                if (unbondingMsg != null) {
                    UnbondingLockNotice(message = unbondingMsg)
                }

                // At cosmos-sdk's MaxEntries cap a further undelegate is rejected on-chain —
                // surface
                // it inline and disable Continue so the user isn't sent into a doomed MPC ceremony.
                if (state.maxUnbondingEntriesReached) {
                    UnbondingLockNotice(
                        message =
                            stringResource(
                                R.string.cosmos_staking_max_entries_reached,
                                CosmosStakingConfig.MAX_ENTRIES,
                            )
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
                    if (state.isSubmitting || state.maxUnbondingEntriesReached)
                        VsButtonState.Disabled
                    else VsButtonState.Enabled,
                onClick = viewModel::submit,
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(16.dp),
            )
        }
    }
}

/**
 * Unstake amount card — mirrors Figma "Unstake LUNA": centered amount with a live "{percent}%"
 * subline and a 0–100% drag slider (with 25/50/75 tick stops), rather than the 25/50/75/Max chips
 * the Stake form uses. The slider is the primary control; the percentage drives the amount field.
 */
@Composable
private fun UnstakeAmountCard(
    ticker: String,
    amountFieldState: TextFieldState,
    available: BigDecimal,
    percentageSelected: Int,
    onPercentage: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
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
        // Amount + live "{percent}%" subline, vertically centered in the card slack (SendForm).
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            TokenAmountInput(
                primaryFieldState = amountFieldState,
                primaryLabel = ticker.ifEmpty { "Token" },
                secondaryText = "${percentageSelected.coerceIn(0, 100)}%",
                maxBalance = available.takeIf { it > BigDecimal.ZERO },
                modifier = Modifier.fillMaxWidth().align(Alignment.Center),
            )
        }
        UnstakePercentSlider(
            percent = (percentageSelected.coerceIn(0, 100)) / 100f,
            onPercentChanged = { onPercentage((it * 100).toInt()) },
        )
        BalanceAvailableRow(ticker = ticker, available = available)
    }
}

/**
 * 0% … slider … 100% with a pill thumb. Drags smoothly (continuous) and snaps to the nearest
 * quarter (0/25/50/75/100%) on release, with three quarter tick dots drawn on the track.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UnstakePercentSlider(percent: Float, onPercentChanged: (Float) -> Unit) {
    val tickColor = Theme.v2.colors.border.normal
    // Local drag value so the thumb glides freely; the snapped quarter is committed on release.
    var sliderValue by remember(percent) { mutableFloatStateOf(percent) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = "0%",
            style = Theme.brockmann.body.xs.medium,
            color = Theme.v2.colors.text.tertiary,
        )
        Column(modifier = Modifier.weight(1f)) {
            Spacer(modifier = Modifier.height(4.dp))
            Slider(
                value = sliderValue,
                // Push the live value while dragging so the amount + "{percent}%" subline track the
                // thumb; snap to the nearest quarter on release.
                onValueChange = {
                    sliderValue = it
                    onPercentChanged(it)
                },
                onValueChangeFinished = {
                    val snapped = (sliderValue * 4).roundToInt() / 4f
                    sliderValue = snapped
                    onPercentChanged(snapped)
                },
                valueRange = 0f..1f,
                thumb = {
                    Box(
                        modifier =
                            Modifier.shadow(
                                    elevation = 4.dp,
                                    spotColor = Theme.v2.colors.shadow.low,
                                    ambientColor = Theme.v2.colors.shadow.low,
                                )
                                .width(38.dp)
                                .height(24.dp)
                                .background(
                                    color = Theme.v2.colors.text.primary,
                                    shape = RoundedCornerShape(100),
                                )
                    )
                },
                track = { sliderState ->
                    Box {
                        SliderDefaults.Track(
                            sliderState = sliderState,
                            colors =
                                SliderDefaults.colors(
                                    activeTrackColor = Theme.v2.colors.primary.accent3,
                                    inactiveTrackColor = Theme.v2.colors.border.normal,
                                    thumbColor = Theme.v2.colors.text.primary,
                                ),
                            thumbTrackGapSize = 0.dp,
                            modifier = Modifier.height(6.dp),
                        )
                        // Quarter tick dots (25/50/75%) — drawn manually since the slider is
                        // continuous (no `steps`, so SliderDefaults stop-indicators don't apply).
                        Canvas(modifier = Modifier.matchParentSize()) {
                            listOf(0.25f, 0.5f, 0.75f).forEach { fraction ->
                                drawCircle(
                                    color = tickColor,
                                    radius = 2.dp.toPx(),
                                    center = Offset(x = size.width * fraction, y = size.height / 2f),
                                )
                            }
                        }
                    }
                },
            )
        }
        Text(
            text = "100%",
            style = Theme.brockmann.body.xs.medium,
            color = Theme.v2.colors.text.tertiary,
        )
    }
}

/**
 * Read-only validator row for the unstake / redelegate-source slot. Styled like the Stake screen's
 * "Validator" picker button (bordered row, "Validator" label + avatar + moniker), but without the
 * picker affordance since the validator is fixed by the caller.
 */
@Composable
internal fun ValidatorReadonlyBlock(moniker: String, address: String) {
    Row(
        modifier =
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .border(
                    width = 1.dp,
                    color = Theme.v2.colors.border.normal,
                    shape = RoundedCornerShape(12.dp),
                )
                .padding(horizontal = 16.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.cosmos_staking_validator_picker),
            style = Theme.brockmann.body.s.medium,
            color = Theme.v2.colors.text.primary,
        )
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ValidatorAvatar(
                avatarUrl = null,
                monogram = moniker.ifEmpty { address }.take(1).uppercase(),
                size = 24.dp,
                colorKey = address,
            )
            UiSpacer(size = 8.dp)
            Text(
                text = moniker.ifEmpty { truncated(address) },
                style = Theme.brockmann.body.s.medium,
                color = Theme.v2.colors.text.secondary,
                maxLines = 1,
            )
        }
    }
}

@Composable
internal fun UnbondingLockNotice(message: String) {
    Row(
        modifier =
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .border(
                    width = 1.dp,
                    color = Theme.v2.colors.alerts.warning,
                    shape = RoundedCornerShape(12.dp),
                )
                .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "⚠",
            style = Theme.brockmann.body.s.medium,
            color = Theme.v2.colors.alerts.warning,
        )
        UiSpacer(size = 8.dp)
        Text(
            text = message,
            style = Theme.brockmann.supplementary.caption,
            color = Theme.v2.colors.text.primary,
        )
    }
}

@Preview
@Composable
private fun CosmosUndelegateScreenPreview() {
    V2Scaffold(title = "Unstake LUNA", onBackClick = {}) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(16.dp).padding(bottom = 80.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ValidatorReadonlyBlock(moniker = "Allnodes", address = "terravaloper1allnodes78wk")
                UnstakeAmountCard(
                    ticker = "LUNA",
                    amountFieldState = rememberTextFieldState("2.5"),
                    available = BigDecimal("2.5"),
                    percentageSelected = 100,
                    onPercentage = {},
                    modifier = Modifier.weight(1f),
                )
                UnbondingLockNotice(
                    message = "Funds are locked for 21 days. Available on Jul 24, 2026."
                )
            }
            VsButton(
                label = "Continue",
                variant = VsButtonVariant.CTA,
                state = VsButtonState.Enabled,
                onClick = {},
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(16.dp),
            )
        }
    }
}

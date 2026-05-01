package com.vultisig.wallet.ui.screens.v2.defi.maya

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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.UiGradientHorizontalDivider
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.buttons.VsButton
import com.vultisig.wallet.ui.components.buttons.VsButtonState
import com.vultisig.wallet.ui.models.deposit.DepositFormUiModel
import com.vultisig.wallet.ui.models.deposit.DepositFormViewModel
import com.vultisig.wallet.ui.screens.v2.defi.model.DeFiNavActions
import com.vultisig.wallet.ui.theme.Theme
import com.vultisig.wallet.ui.utils.UiText
import com.vultisig.wallet.ui.utils.asString

@Composable
internal fun RemoveLpScreen(
    vaultId: String,
    chainId: String,
    poolId: String? = null,
    model: DepositFormViewModel = hiltViewModel(),
) {
    val state by model.state.collectAsState()

    LaunchedEffect(vaultId, chainId, poolId) {
        model.loadData(
            vaultId = vaultId,
            chainId = chainId,
            depositType = DeFiNavActions.REMOVE_LP.type,
            bondAddress = null,
            poolId = poolId,
        )
    }

    RemoveLpScreenContent(
        state = state,
        onPercentChanged = model::setRemoveLpPercent,
        onContinue = model::deposit,
    )
}

@Composable
internal fun RemoveLpScreenContent(
    state: DepositFormUiModel = DepositFormUiModel(),
    onPercentChanged: (Float) -> Unit = {},
    onContinue: () -> Unit = {},
) {
    Column(
        modifier =
            Modifier.fillMaxSize()
                .background(Theme.v2.colors.backgrounds.primary)
                .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Amount card
        val amountCardShape = RoundedCornerShape(12.dp)
        Column(
            modifier =
                Modifier.fillMaxWidth()
                    .weight(1f)
                    .clip(amountCardShape)
                    .border(
                        width = 1.dp,
                        color = Theme.v2.colors.border.normal,
                        shape = amountCardShape,
                    )
                    .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.remove_pool_amount_label),
                    style = Theme.brockmann.body.s.medium,
                    color = Theme.v2.colors.text.primary,
                )
            }

            UiSpacer(12.dp)

            UiGradientHorizontalDivider()

            Box(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text =
                            if (state.removeLpCacaoDisplay.isEmpty())
                                stringResource(
                                    R.string.remove_pool_zero_amount,
                                    state.removeLpTokenSymbol,
                                )
                            else
                                stringResource(
                                    R.string.remove_pool_amount_format,
                                    state.removeLpCacaoDisplay,
                                    state.removeLpTokenSymbol,
                                ),
                        style = Theme.brockmann.headings.largeTitle,
                        color = Theme.v2.colors.text.primary,
                        textAlign = TextAlign.Center,
                    )

                    Text(
                        text =
                            stringResource(
                                R.string.remove_pool_percent_format,
                                (state.removeLpPercent * 100).toInt(),
                            ),
                        style = Theme.brockmann.body.m.medium,
                        color = Theme.v2.colors.text.tertiary,
                        textAlign = TextAlign.Center,
                    )
                }
            }

            RemoveLpSlider(
                percent = state.removeLpPercent,
                onPercentChanged = onPercentChanged,
                enabled = state.availableLpUnits != null && state.removeLpUnitsDivisor.signum() > 0,
                modifier = Modifier.fillMaxWidth(),
            )

            UiSpacer(size = 12.dp)
            // Balance row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.send_form_balance_available),
                    style = Theme.brockmann.body.s.medium,
                    color = Theme.v2.colors.text.secondary,
                )

                Text(
                    text = state.balance.asString(),
                    style = Theme.brockmann.body.s.medium,
                    color = Theme.v2.colors.text.secondary,
                )
            }
        }

        UiSpacer(size = 43.dp)

        // Continue button
        VsButton(
            label = stringResource(R.string.send_continue_button),
            modifier = Modifier.fillMaxWidth(),
            onClick = onContinue,
            state =
                if (state.removeLpPercent > 0f && state.removeLpCacaoDisplay.isNotEmpty())
                    VsButtonState.Enabled
                else VsButtonState.Disabled,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RemoveLpSlider(
    percent: Float,
    onPercentChanged: (Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val tickColor = Theme.v2.colors.border.normal
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = stringResource(R.string.remove_pool_percent_format, 0),
            style = Theme.brockmann.body.xs.medium,
            color = Theme.v2.colors.text.tertiary,
        )
        Column(verticalArrangement = Arrangement.Center, modifier = Modifier.weight(1f)) {
            Spacer(modifier = Modifier.height(4.dp))
            Slider(
                value = percent,
                onValueChange = onPercentChanged,
                enabled = enabled,
                valueRange = 0f..1f,
                steps = 3,
                thumb = {
                    Box(
                        modifier =
                            Modifier.shadow(
                                    elevation = 13.dp,
                                    spotColor = Theme.v2.colors.shadow.low,
                                    ambientColor = Theme.v2.colors.shadow.low,
                                )
                                .shadow(
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
                    SliderDefaults.Track(
                        sliderState = sliderState,
                        colors =
                            SliderDefaults.colors(
                                activeTrackColor = Theme.v2.colors.primary.accent3,
                                inactiveTrackColor = Theme.v2.colors.border.normal,
                                thumbColor = Theme.v2.colors.text.primary,
                            ),
                        thumbTrackGapSize = 0.dp,
                        drawStopIndicator = { offset ->
                            drawCircle(
                                color = tickColor,
                                radius = 2.dp.toPx(),
                                center = offset.copy(y = offset.y + 9.dp.toPx()),
                            )
                        },
                        modifier = Modifier.height(6.dp),
                    )
                },
            )
        }
        Text(
            text = stringResource(R.string.remove_pool_percent_format, 100),
            style = Theme.brockmann.body.xs.medium,
            color = Theme.v2.colors.text.tertiary,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun RemoveLpScreenContentPreview() {
    Box(modifier = Modifier.background(Theme.v2.colors.backgrounds.primary)) {
        RemoveLpScreenContent(
            state =
                DepositFormUiModel(
                    removeLpPercent = 0f,
                    removeLpCacaoDisplay = "5.000",
                    removeLpTokenSymbol = "CACAO",
                    availableLpUnits = "1000000",
                    balance = UiText.DynamicString("24,000 CACAO"),
                    removeLpUnitsDivisor = java.math.BigInteger.valueOf(5_000_000L),
                    removeLpPoolDepth = java.math.BigInteger.valueOf(25_000_000L),
                )
        )
    }
}

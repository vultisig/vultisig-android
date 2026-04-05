package com.vultisig.wallet.ui.screens.v2.defi.maya

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.graphics.Color
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

    LaunchedEffect(Unit) {
        model.loadData(
            vaultId = vaultId,
            chainId = chainId,
            depositType = "remove_lp",
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
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Amount card
        Column(
            modifier =
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Theme.v2.colors.backgrounds.secondary)
                    .padding(horizontal = 16.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = stringResource(R.string.remove_pool_amount_label),
                style = Theme.brockmann.body.s.medium,
                color = Theme.v2.colors.text.tertiary,
                textAlign = TextAlign.Center,
            )

            UiSpacer(size = 8.dp)

            Text(
                text = state.removeLpCacaoDisplay.ifEmpty { "0 CACAO" },
                style = Theme.brockmann.headings.largeTitle,
                color = Theme.v2.colors.text.primary,
                textAlign = TextAlign.Center,
            )

            Text(
                text = "${(state.removeLpPercent * 100).toInt()}%",
                style = Theme.brockmann.body.m.medium,
                color = Theme.v2.colors.text.tertiary,
                textAlign = TextAlign.Center,
            )

            UiSpacer(size = 8.dp)

            UiGradientHorizontalDivider()

            UiSpacer(size = 8.dp)

            RemoveLpSlider(
                percent = state.removeLpPercent,
                onPercentChanged = onPercentChanged,
                modifier = Modifier.fillMaxWidth(),
            )

            UiSpacer(size = 8.dp)
        }

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

        UiSpacer(size = 16.dp)

        // Continue button
        VsButton(
            label = stringResource(R.string.send_continue_button),
            modifier = Modifier.fillMaxWidth(),
            onClick = onContinue,
            state =
                if (state.removeLpPercent > 0f && state.availableLpUnits != null)
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
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = "0%",
            style = Theme.brockmann.supplementary.caption,
            color = Theme.v2.colors.text.tertiary,
        )
        UiSpacer(12.dp)
        Column(modifier = Modifier.weight(1f)) {
            Slider(
                value = percent,
                onValueChange = onPercentChanged,
                valueRange = 0f..1f,
                steps = 0,
                modifier = Modifier.fillMaxWidth(),
                thumb = {
                    Box(
                        modifier =
                            Modifier.shadow(
                                    elevation = 13.dp,
                                    spotColor = Color(0x1F000000),
                                    ambientColor = Color(0x1F000000),
                                )
                                .shadow(
                                    elevation = 4.dp,
                                    spotColor = Color(0x1F000000),
                                    ambientColor = Color(0x1F000000),
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
                        drawStopIndicator = null,
                        modifier = Modifier.height(6.dp),
                    )
                },
            )

            // Tick marks below the track
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                repeat(5) {
                    Box(
                        modifier =
                            Modifier.size(4.dp)
                                .background(
                                    color = Theme.v2.colors.border.normal,
                                    shape = CircleShape,
                                )
                    )
                }
            }
        }
        UiSpacer(12.dp)
        Text(
            text = "100%",
            style = Theme.brockmann.supplementary.caption,
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
                    removeLpPercent = .5f,
                    removeLpCacaoDisplay = "5.000 CACAO",
                    availableLpUnits = "1000000",
                    balance = UiText.DynamicString("24,000 CACAO"),
                    selectedPoolTotalLpUnits = 5_000_000L,
                    selectedPoolCacaoDepth = 25_000_000L,
                )
        )
    }
}

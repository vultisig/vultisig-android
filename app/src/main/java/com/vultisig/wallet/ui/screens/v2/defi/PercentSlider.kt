package com.vultisig.wallet.ui.screens.v2.defi

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.theme.Theme

/** A 0–100% slider with end labels, a pill thumb and tick marks under the track's [steps] stops. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PercentSlider(
    percent: Float,
    onPercentChanged: (Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    steps: Int = 3,
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
                steps = steps,
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
                                    shape = Theme.v2.radius.pill,
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

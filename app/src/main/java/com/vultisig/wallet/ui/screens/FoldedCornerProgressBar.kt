package com.vultisig.wallet.ui.screens// kotlin
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.buttons.AutoSizingText
import com.vultisig.wallet.ui.theme.Theme
import com.vultisig.wallet.ui.theme.v2.V2.colors


@Composable
fun ResourceUsageCard(
    modifier: Modifier = Modifier, availableResourceAmount: Long,
    totalResourceAmount: Long, resourceIconResId: Int, resourceName: String
) {


    Box(
        modifier = modifier
            .border(
                width = 1.dp,
                color = colors.backgrounds.tertiary,
                shape = RoundedCornerShape(size = 8.dp)
            )
            .width(360.dp)
            .height(80.dp)
            .clip(RoundedCornerShape(8.dp))
            .padding(9.dp)
    ) {

            Column(
                modifier = Modifier
                    .width(171.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                AutoSizingText(
                    text = resourceName,
                    color = colors.alerts.success,
                    style = Theme.brockmann.body.s.medium,

                    )

                UiSpacer(8.dp)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(color = colors.backgrounds.secondary)
                    ) {
                        Image(
                            painter = painterResource(R.drawable.bandwidth),
                            contentDescription = "Bandwidth Icon",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .size(16.dp)
                                .align(Alignment.Center)
                        )
                    }
                    Column {
                        AutoSizingText(
                            text = "${availableResourceAmount.toInt()}/${totalResourceAmount.toInt()}",
                            style = Theme.brockmann.supplementary.caption,
                            color = colors.text.light,
                        )
                        UiSpacer(8.dp)
                        FoldedCornerProgressBar(
                            targetValue = if (totalResourceAmount > 0) availableResourceAmount.toFloat() / totalResourceAmount.toFloat() else 0f,
                            color = colors.alerts.success,
                        )
                    }
                }
            }

            Spacer(
                modifier = Modifier
                    .size(1.dp)
                    .background(colors.backgrounds.tertiary)
            )

            Column(
                modifier = Modifier
                    .width(171.dp)
                    .align(Alignment.TopEnd),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                AutoSizingText(
                    text = stringResource(R.string.energy),
                    color = colors.alerts.warning,
                    style = Theme.brockmann.body.s.medium,

                    )

                UiSpacer(8.dp)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(color = colors.backgrounds.secondaryNeutral)
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.energy),
                            contentDescription = "Bandwidth Icon",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .size(16.dp)
                                .align(Alignment.Center)
                        )
                    }
                    Column {
                        AutoSizingText(
                            text = "${availableResourceAmount.toInt()}/${totalResourceAmount.toInt()}",
                            style = Theme.brockmann.supplementary.caption,
                            color = colors.text.light,
                        )
                        UiSpacer(8.dp)
                        FoldedCornerProgressBar(
                            targetValue = if (totalResourceAmount > 0) availableResourceAmount.toFloat() / totalResourceAmount.toFloat() else 0f,
                            color = colors.alerts.warning,
                        )
                    }
                }
            }

    }
}


@Composable
fun FoldedCornerProgressBar(
    targetValue: Float = 0.6f,
    color: Color = colors.alerts.success,

    ) {

    val size by animateFloatAsState(
        targetValue = targetValue,
        tween(
            durationMillis = 1000,
            delayMillis = 200,
            easing = LinearOutSlowInEasing
        )
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(8.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(9.dp))
                .background(color = colors.backgrounds.surface2)
        )
        Box(
            modifier = Modifier
                .fillMaxWidth(size)
                .fillMaxHeight()
                .clip(RoundedCornerShape(9.dp))
                .background(color)
                .animateContentSize()
        )
    }

}

@Preview
@Composable
private fun TronBandwidthCardPreview() {
    ResourceUsageCard(
        resourceIconResId = R.drawable.bandwidth,
        resourceName = "Bandwidth",
        availableResourceAmount = 240,
        totalResourceAmount = 400
    )
}

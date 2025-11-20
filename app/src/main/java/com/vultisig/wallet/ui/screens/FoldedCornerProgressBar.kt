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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.theme.Theme


@Composable
fun TronBandwidthCard(
    backgroundColor: Color,
    unUsed: Long,
    total: Long,
    icon: Int,
    text: String
) {
    Box(
        modifier = Modifier
            .border(
                width = 1.dp,
                color = Theme.colors.backgrounds.tertiary,
                shape = RoundedCornerShape(size = 8.dp)
            )
            .width(170.dp)
            .height(84.dp)
            .clip(RoundedCornerShape(8.dp))
            .padding(9.dp)
    ) {


        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopStart),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Start,
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(backgroundColor)
                ) {
                    Image(
                        painter = painterResource(id = icon),
                        contentDescription = "Bandwidth Icon",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .size(20.dp)
                            .align(Alignment.Center)
                    )
                }
                UiSpacer(6.dp)
                Column {
                    Text(
                        text = text,
                        color = Theme.colors.text.light,
                        style = Theme.brockmann.body.s.medium,
                        textAlign = TextAlign.Center
                    )
                    UiSpacer(1.dp)
                    Text(
                        text = "${unUsed.toInt()}/${total.toInt()}",
                        style = Theme.brockmann.supplementary.caption,
                        color = Theme.colors.text.light,
                        textAlign = TextAlign.Center
                    )
                }
            }
            UiSpacer(7.dp)
            FoldedCornerProgressBar(
                targetValue = if (total > 0) unUsed.toFloat() / total.toFloat() else 0f
            )
        }
    }
}


@Composable
fun FoldedCornerProgressBar(
    targetValue: Float = 0.6f,

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
                .background(color = Theme.colors.backgrounds.darkBlue)
        )
        Box(
            modifier = Modifier
                .fillMaxWidth(size)
                .fillMaxHeight()
                .clip(RoundedCornerShape(9.dp))
                .background(Theme.colors.backgrounds.blue)
                .animateContentSize()
        )
    }

}

@Preview
@Composable
private fun TronBandwidthCardPreview() {
    TronBandwidthCard(
        icon = R.drawable.bandwidth,
        backgroundColor = Theme.colors.backgrounds.success,
        text = "Bandwidth",
        unUsed = 240,
        total = 400
    )
}

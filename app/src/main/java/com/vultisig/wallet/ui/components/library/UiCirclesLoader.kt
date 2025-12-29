package com.vultisig.wallet.ui.components.library

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import com.vultisig.wallet.ui.theme.Theme

@Preview
@Composable
internal fun UiCirclesLoader(
    modifier: Modifier = Modifier,
    color1: Color = Theme.colors.backgrounds.teal,
    color2: Color = Theme.colors.primary.accent4,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "UiCirclesLoader")
    val circleX = infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(565, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "UiCirclesLoader"
    )

    Canvas(
        modifier = modifier
            .size(
                width = 56.dp,
                height = 24.dp
            )
    ) {
        val circleRadius = size.minDimension / 2
        val y = size.height / 2
        val endX = size.width - circleRadius

        drawCircle(
            color = color1,
            center = Offset(
                x = lerp(circleRadius, endX, circleX.value),
                y = y
            ),
            radius = circleRadius,
        )
        drawCircle(
            color = color2,
            center = Offset(
                x = lerp(circleRadius, endX, 1f - circleX.value),
                y = y
            ),
            radius = circleRadius,
        )
    }
}
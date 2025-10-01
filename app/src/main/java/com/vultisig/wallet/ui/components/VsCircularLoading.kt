package com.vultisig.wallet.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.vultisig.wallet.ui.theme.Theme

@Composable
internal fun VsCircularLoading(
    modifier: Modifier = Modifier,
    strokeWidth: Float = 10f,
    color1: Color = Theme.colors.buttons.primary,
    color2: Color = Color.Transparent,
) {
    val infiniteTransition = rememberInfiniteTransition(
        label = "VsCircularLoadingTransition"
    )
    val angle by infiniteTransition.animateFloat(
        initialValue = 0F,
        targetValue = 360F,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = 1000,
                easing = LinearEasing
            )
        ),
        label = "angleAnimation"
    )
    Canvas(modifier = modifier.rotate(angle)) {
        drawArc(
            brush = Brush.sweepGradient(
                0f to color2,
                0.95f to color1,
                0.96f to color2, // there was a problem with start of the gradient
            ),
            size = Size(size.width - strokeWidth, size.height - strokeWidth),
            topLeft = Offset(strokeWidth / 2, strokeWidth / 2),
            startAngle = 0f,
            sweepAngle = 300f,
            useCenter = false,
            style = Stroke(
                width = strokeWidth,
                cap = StrokeCap.Round,
            ),
        )
    }
}

@Preview
@Composable
private fun VsCircularLoadingPreview() {
    VsCircularLoading(
        modifier = Modifier.size(50.dp)
    )
}
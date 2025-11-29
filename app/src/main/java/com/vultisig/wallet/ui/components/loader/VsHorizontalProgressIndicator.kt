package com.vultisig.wallet.ui.components.loader

import android.graphics.BlurMaskFilter
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.vultisig.wallet.ui.theme.Theme

@Composable
internal fun VsHorizontalProgressIndicator(
    progress: Float,
    modifier: Modifier = Modifier,
    height: Dp = 2.dp,
) {
    val indicatorBrush = Theme.v2.colors.gradients.primaryReversed
    val glowingColor = Theme.v2.colors.buttons.tertiary

    val targetProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(
            durationMillis = 1000,
        ),
        label = "VsHorizontalProgressIndicator.Progress",
    )

    Canvas(
        modifier = modifier
            .height(height),
    ) {
        val paint = Paint().apply {
            color = glowingColor
            this.asFrameworkPaint().apply {
                maskFilter = BlurMaskFilter(
                    36f,
                    BlurMaskFilter.Blur.NORMAL,
                )
            }
        }
        drawIntoCanvas {
            it.drawRoundRect(
                left = 0f,
                top = 0f,
                right = size.width * targetProgress,
                bottom = size.height,
                radiusX = size.width,
                radiusY = size.height,
                paint = paint,
            )
        }
        drawRoundRect(
            brush = indicatorBrush,
            size = size.copy(width = size.width * targetProgress),
        )
    }
}


@Preview
@Composable
private fun VsHorizontalProgressIndicatorPreview() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(24.dp)
            .background(Theme.v2.colors.backgrounds.primary),
        verticalArrangement = Arrangement.Center,
    ) {
        VsHorizontalProgressIndicator(
            progress = 0.5f,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
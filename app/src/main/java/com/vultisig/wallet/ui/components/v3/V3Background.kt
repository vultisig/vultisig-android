package com.vultisig.wallet.ui.components.v3

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.center
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.tooling.preview.Preview
import com.vultisig.wallet.ui.theme.Theme

@Composable
@Preview
fun V3Background(
    backgroundColor: Color = Theme.v2.colors.backgrounds.primary,
    shineSpotColor: Color = Theme.v2.colors.buttons.ctaPrimary,
) {
    Box(
        modifier =
            Modifier.fillMaxSize().background(backgroundColor).drawWithCache {
                val width = size.width
                val height = size.height
                val radialBrush =
                    Brush.radialGradient(
                        colors = listOf(shineSpotColor.copy(alpha = 0.75f), Color.Transparent),
                        center = size.center,
                        radius = minOf(width, height) / 2,
                    )
                val verticalBrush =
                    Brush.verticalGradient(
                        colors = listOf(backgroundColor.copy(alpha = 0.5f), Color.Transparent),
                        endY = height / 4,
                    )
                onDrawBehind {
                    val scaleX = 1.5f
                    val scaleY = 1.75f
                    withTransform({
                        scale(
                            scaleX = scaleX,
                            scaleY = scaleY,
                            pivot = center.copy(y = height + 100f),
                        )
                    }) {
                        drawCircle(
                            brush = radialBrush,
                            radius = minOf(width, height) / 2,
                            center = center,
                        )
                    }
                    drawRect(brush = verticalBrush, size = size.copy(height = height / 4))
                }
            }
    )
}

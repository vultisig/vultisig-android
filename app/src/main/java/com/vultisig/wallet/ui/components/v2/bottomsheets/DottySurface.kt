package com.vultisig.wallet.ui.components.v2.bottomsheets

import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.CacheDrawScope
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.dp
import com.vultisig.wallet.ui.theme.Theme
import com.vultisig.wallet.ui.theme.v2.V2.colors

/** The rounded, dotted surface the app's bottom sheets are drawn on. */
@Composable
internal fun Modifier.dottySurface(): Modifier {
    val surfaceColor = Theme.v2.colors.backgrounds.primary
    val fadeColor = colors.backgrounds.secondary
    return clip(shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp))
        .background(surfaceColor)
        .drawWithCache {
            val dots = rememberDotsPath(stepSize = 72f, dotRadius = 2.5f, dotColor = DotColor)
            val fadeBrush = Brush.verticalGradient(colors = listOf(Color.Transparent, fadeColor))
            onDrawBehind {
                drawPath(dots.path, color = dots.color)
                drawRect(brush = fadeBrush)
            }
        }
}

private val DotColor = Color(0xff172854)

private data class DotsPath(val path: Path, val color: Color)

private fun CacheDrawScope.rememberDotsPath(
    stepSize: Float = 72f,
    dotRadius: Float = 2.5f,
    dotColor: Color = colors.neutrals.n50,
): DotsPath {
    val width = size.width
    val height = size.height

    val dotsX = (width / stepSize).toInt() + 1
    val dotsY = (height / stepSize).toInt() + 1

    val offsetX = (width - (dotsX - 1) * stepSize) / 2
    val offsetY = (height - (dotsY - 1) * stepSize) / 2

    val path = Path()
    for (row in 0 until dotsY) {
        for (col in 0 until dotsX) {
            val x = offsetX + col * stepSize
            val y = offsetY + row * stepSize
            path.addOval(
                Rect(
                    left = x - dotRadius,
                    top = y - dotRadius,
                    right = x + dotRadius,
                    bottom = y + dotRadius,
                )
            )
        }
    }
    return DotsPath(path = path, color = dotColor)
}

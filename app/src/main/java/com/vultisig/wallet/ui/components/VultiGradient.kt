package com.vultisig.wallet.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.vultisig.wallet.ui.theme.Theme

private val SETTINGS_SHADE_COLOR = Color(0xFF34DAE9)

@Composable
internal fun Brush.Companion.vultiGradient() =
    horizontalGradient(
        colors = listOf(Theme.v2.colors.buttons.primary, Theme.v2.colors.primary.accent2)
    )

internal fun Brush.Companion.vultiCircleShadeGradient() =
    radialGradient(colors = listOf(Color(0Xff33e6bf).copy(alpha = 0.1f), Color.Transparent))

/** Draws a soft radial wash, stretched from [glowCenter] by [scaleX] and [scaleY]. */
internal fun DrawScope.drawSoftGlow(
    glowCenter: Offset,
    radius: Float,
    stops: Array<Pair<Float, Color>>,
    scaleX: Float = 1f,
    scaleY: Float = 1f,
) =
    scale(scaleX = scaleX, scaleY = scaleY, pivot = glowCenter) {
        drawCircle(
            brush = Brush.radialGradient(colorStops = stops, center = glowCenter, radius = radius),
            radius = radius,
            center = glowCenter,
        )
    }

/** The sub-screens' wide blurred shade, centred [centerOffsetY] from the element's middle. */
internal fun Modifier.settingsShade(centerOffsetY: Dp): Modifier = drawBehind {
    drawSoftGlow(
        glowCenter = center.copy(y = center.y + centerOffsetY.toPx()),
        radius = SETTINGS_SHADE_RADIUS.toPx(),
        stops = SETTINGS_SHADE_STOPS,
        scaleX = SETTINGS_SHADE_WIDTH_RATIO,
    )
}

private val SETTINGS_SHADE_RADIUS = 400.dp
private const val SETTINGS_SHADE_WIDTH_RATIO = 1.2f
private val SETTINGS_SHADE_STOPS =
    arrayOf(
        0f to SETTINGS_SHADE_COLOR.copy(alpha = 0.1f),
        0.2f to SETTINGS_SHADE_COLOR.copy(alpha = 0.088f),
        0.4f to SETTINGS_SHADE_COLOR.copy(alpha = 0.06f),
        0.6f to SETTINGS_SHADE_COLOR.copy(alpha = 0.03f),
        0.8f to SETTINGS_SHADE_COLOR.copy(alpha = 0.012f),
        1f to Color.Transparent,
    )

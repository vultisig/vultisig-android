package com.vultisig.wallet.ui.components.util

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp

fun Modifier.dashedBorder(
    width: Dp,
    color: Color,
    cornerRadius: Dp,
    dashLength: Dp,
    intervalLength: Dp,
) = drawBehind {
    drawRoundRect(
        color = color,
        style = Stroke(
            width = width.toPx(),
            pathEffect = PathEffect.dashPathEffect(
                floatArrayOf(
                    dashLength.toPx(),
                    intervalLength.toPx()
                ), 0f
            )
        ),
        cornerRadius = CornerRadius(cornerRadius.toPx())
    )
}
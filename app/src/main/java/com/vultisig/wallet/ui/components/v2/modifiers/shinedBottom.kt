package com.vultisig.wallet.ui.components.v2.modifiers

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.center
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.translate

internal fun Modifier.shinedBottom(
    color : Color = Color.White
) = this
    .drawBehind {
        translate(
            top = this.size.center.y
        ) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        color,
                        Color.Transparent,
                    ),
                ),
                alpha = 0.1f,
            )
        }
    }
package com.vultisig.wallet.ui.components.modifiers

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.center
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.translate

internal fun Modifier.shinedBottom() = this
    .drawBehind {
        translate(
            top = this.size.center.y
        ) {
            drawCircle(
                brush = Brush.Companion.radialGradient(
                    colors = listOf(
                        Color.Companion.White,
                        Color.Companion.Transparent,
                    ),
                ),
                alpha = 0.1f,
            )
        }
    }
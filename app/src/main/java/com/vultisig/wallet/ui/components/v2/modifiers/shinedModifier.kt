package com.vultisig.wallet.ui.components.v2.modifiers

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.center
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.translate

internal fun Modifier.shinedBottom(color: Color = Color.White, shineAlpha: Float = 0.1f) =
    this.drawWithCache {
        val brush = Brush.radialGradient(colors = listOf(color, Color.Transparent))
        onDrawBehind {
            translate(top = this.size.center.y) { drawCircle(brush = brush, alpha = shineAlpha) }
        }
    }

internal fun Modifier.shinedCenter(color: Color = Color.White, shineAlpha: Float = 0.1f) =
    this.drawWithCache {
        val brush = Brush.radialGradient(colors = listOf(color, Color.Transparent))
        onDrawBehind { drawCircle(brush = brush, alpha = shineAlpha) }
    }

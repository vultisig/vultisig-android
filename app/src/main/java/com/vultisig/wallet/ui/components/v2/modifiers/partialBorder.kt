package com.vultisig.wallet.ui.components.v2.modifiers


import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp

@Composable
internal fun Modifier.partialBorder(
    width: Dp,
    color: Color,
    shape: Shape
): Modifier = border(
    border = BorderStroke(
        width = width,
        brush = Brush.linearGradient(
            colors = listOf(
                color, // top-left
                Color.Transparent,
                Color.Transparent,
                color, // bottom-right
            ),
        )
    ),
    shape = shape
)

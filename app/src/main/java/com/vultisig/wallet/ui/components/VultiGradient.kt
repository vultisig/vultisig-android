package com.vultisig.wallet.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.vultisig.wallet.ui.theme.Theme

@Composable
internal fun Brush.Companion.vultiGradient() = horizontalGradient(
    colors = listOf(
        Theme.colors.turquoise600Main,
        Theme.colors.persianBlue600Main
    )
)

internal fun Brush.Companion.vultiCircleShadeGradient() = radialGradient(
    colors = listOf(
        Color(0Xff33e6bf).copy(alpha = 0.1f),
        Color.Transparent,
    ),
)
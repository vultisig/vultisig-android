package com.vultisig.wallet.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import com.vultisig.wallet.ui.theme.Theme

@Composable
internal fun Brush.Companion.vultiGradient() = horizontalGradient(
    colors = listOf(
        Theme.colors.turquoise600Main,
        Theme.colors.persianBlue600Main
    )
)
@Composable
internal fun Brush.Companion.vultiGradientV2() = horizontalGradient(
    colors = listOf(
        Theme.colors.buttons.primary,
        Theme.colors.persianBlue600Main
    ),
)
package com.vultisig.wallet.ui.utils

import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp

val LocalWindowSizeClass = staticCompositionLocalOf<WindowSizeClass?> { null }

@Composable
fun rememberWindowWidthSizeClass(): WindowWidthSizeClass {
    val provided = LocalWindowSizeClass.current
    if (provided != null) return provided.widthSizeClass

    val widthPx = LocalWindowInfo.current.containerSize.width
    val widthDp = with(LocalDensity.current) { widthPx.toDp() }
    return when {
        widthDp < 600.dp -> WindowWidthSizeClass.Compact
        widthDp < 840.dp -> WindowWidthSizeClass.Medium
        else -> WindowWidthSizeClass.Expanded
    }
}

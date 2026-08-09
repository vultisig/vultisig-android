package com.vultisig.wallet.ui.theme.v2

import androidx.compose.runtime.staticCompositionLocalOf

object V2 {
    val colors: Colors = Colors()
    val radius: Radii = Radii()
}

val LocalV2Theme = staticCompositionLocalOf { V2 }

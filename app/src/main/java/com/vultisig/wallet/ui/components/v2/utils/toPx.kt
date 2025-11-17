package com.vultisig.wallet.ui.components.v2.utils

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp

@Composable
internal fun Dp.toPx() = with(LocalDensity.current) {
    toPx()
}
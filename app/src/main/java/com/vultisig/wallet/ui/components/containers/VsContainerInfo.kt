package com.vultisig.wallet.ui.components.containers

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.vultisig.wallet.ui.theme.Theme.colors

internal enum class VsContainerType {
    PRIMARY,
    SECONDARY,
    TERTIARY,
}

internal sealed interface VsContainerBorderType {
    object Borderless : VsContainerBorderType
    data class Bordered(val color: Color = colors.border.light) : VsContainerBorderType
}

internal sealed interface VsContainerCornerType {
    object Circular : VsContainerCornerType
    data class RoundedVsContainerCornerShape(val size: Dp = 12.dp) : VsContainerCornerType
}
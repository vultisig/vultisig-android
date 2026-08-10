package com.vultisig.wallet.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.unit.dp
import com.vultisig.wallet.ui.theme.Theme

internal fun Modifier.vsStyledBackground() = composed {
    border(
            border = BorderStroke(width = 1.dp, color = Theme.v2.colors.border.light),
            shape = Theme.v2.radius.md,
        )
        .background(color = Theme.v2.colors.backgrounds.secondary, shape = Theme.v2.radius.md)
}

package com.vultisig.wallet.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.vultisig.wallet.ui.theme.Theme

@Composable
internal fun Modifier.vsStyledBackground() =
    border(
            border = BorderStroke(width = 1.dp, color = Theme.v2.colors.border.light),
            shape = RoundedCornerShape(12.dp),
        )
        .background(
            color = Theme.v2.colors.backgrounds.secondary,
            shape = RoundedCornerShape(12.dp),
        )

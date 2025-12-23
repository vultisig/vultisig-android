package com.vultisig.wallet.ui.components

import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.vultisig.wallet.ui.theme.Theme

@Composable
internal fun UiHorizontalDivider(
    modifier: Modifier = Modifier,
    color: Color = Theme.colors.backgrounds.tertiary_2,
) {
    HorizontalDivider(
        color = color,
        modifier = modifier,
    )
}
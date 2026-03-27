package com.vultisig.wallet.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import com.vultisig.wallet.ui.theme.Theme

@Composable
internal fun UiGradientHorizontalDivider(modifier: Modifier = Modifier) {
    val colors = Theme.v2.colors
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(
                    Brush.horizontalGradient(
                        0f to colors.backgrounds.secondary,
                        0.495f to colors.backgrounds.gradientMid,
                        1f to colors.backgrounds.secondary,
                    )
                )
    )
}

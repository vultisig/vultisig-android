package com.vultisig.wallet.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.Dp
import com.vultisig.wallet.ui.theme.Theme

@Composable
internal fun UiHorizontalDivider(
    modifier: Modifier = Modifier,
) {
    HorizontalDivider(
        color = Theme.colors.oxfordBlue400,
        modifier = modifier,
    )
}

@Composable
internal fun UiHorizontalDivider(
    brush: Brush,
    modifier: Modifier = Modifier,
    thickness: Dp = DividerDefaults.Thickness,
) {
    Canvas(modifier.fillMaxWidth().height(thickness)) {
        drawLine(
            brush = brush,
            strokeWidth = thickness.toPx(),
            start = Offset(0f, thickness.toPx() / 2),
            end = Offset(size.width, thickness.toPx() / 2),
        )
    }
}
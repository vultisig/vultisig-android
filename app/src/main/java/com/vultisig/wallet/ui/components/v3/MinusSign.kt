package com.vultisig.wallet.ui.components.v3

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.vultisig.wallet.ui.theme.Theme


@Composable
fun MinusSign(
    modifier: Modifier = Modifier,
    color: Color = Theme.v2.colors.neutrals.n50,
    strokeWidth: Dp = 1.25.dp,
    strokeCap: StrokeCap = StrokeCap.Round
) {
    Canvas(modifier = modifier) {
        val strokeWidthPx = strokeWidth.toPx()

        drawLine(
            color = color,
            strokeWidth = strokeWidthPx,
            cap = strokeCap,
            start = Offset(x = 0f, y = size.height / 2),
            end = Offset(x = size.width, y = size.height / 2)
        )
    }
}

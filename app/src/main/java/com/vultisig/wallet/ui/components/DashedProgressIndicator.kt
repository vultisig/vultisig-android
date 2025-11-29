package com.vultisig.wallet.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import com.vultisig.wallet.ui.theme.Theme

@Composable
fun DashedProgressIndicator(
    modifier: Modifier = Modifier,
    progress: Int = 3,
    totalNumberOfBars: Int = 12
) {
    val activeColor = Theme.v2.colors.alerts.success
    val inactiveColor = Theme.v2.colors.border.light
    Canvas(modifier = modifier) {
        val barArea = size.width / totalNumberOfBars
        val barLength = barArea - 8.dp.toPx()

        var nextBarStartPosition = 0F

        for (i in 0..<totalNumberOfBars) {
            val barStartPosition = nextBarStartPosition + 4.dp.toPx()
            val barEndPosition = barStartPosition + barLength

            val start = Offset(x = barStartPosition, y = size.height / 2)
            val end = Offset(x = barEndPosition, y = size.height / 2)

            drawLine(
                cap = StrokeCap.Round,
                color = if (i < progress) activeColor else inactiveColor,
                start = start,
                end = end,
                strokeWidth = 2.dp.toPx(),
            )

            nextBarStartPosition = barEndPosition + 4.dp.toPx()
        }
    }
}
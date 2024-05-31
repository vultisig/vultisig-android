package com.vultisig.wallet.ui.components.library

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.vultisig.wallet.ui.theme.Theme

@Composable
internal fun UiCircularProgressIndicator(
    progress: () -> Float,
    modifier: Modifier = Modifier,
    strokeWidth: Dp = ProgressIndicatorDefaults.CircularStrokeWidth,
    indicatorBrush: Brush = Brush.linearGradient(
        colorStops = arrayOf(
            0.0f to Theme.colors.persianBlue600Main,
            1.0f to Theme.colors.turquoise600Main,
        ),
    ),
) {
    val coercedProgress = { progress().coerceIn(0f, 1f) }

    val trackColor = Theme.colors.oxfordBlue400

    val stroke = with(LocalDensity.current) {
        Stroke(
            width = strokeWidth.toPx(),
            cap = StrokeCap.Round,
        )
    }

    val fullCircle = 360f

    Canvas(
        modifier
            .semantics(mergeDescendants = true) {
                progressBarRangeInfo = ProgressBarRangeInfo(coercedProgress(), 0f..1f)
            }
            .size(48.dp)
    ) {
        val startAngle = 270f
        val sweep = coercedProgress() * fullCircle
        val diameterOffset = stroke.width / 2
        val arcDimen = size.width - 2 * diameterOffset

        drawArc(
            color = trackColor,
            startAngle = startAngle,
            sweepAngle = fullCircle,
            useCenter = false,
            topLeft = Offset(diameterOffset, diameterOffset),
            size = Size(arcDimen, arcDimen),
            style = stroke,
        )

        drawArc(
            startAngle = startAngle,
            sweepAngle = sweep,
            useCenter = false,
            topLeft = Offset(diameterOffset, diameterOffset),
            size = Size(arcDimen, arcDimen),
            style = stroke,
            brush = indicatorBrush,
        )
    }
}

@Preview
@Composable
private fun CircularProgressIndicatorPreview() {
    UiCircularProgressIndicator(progress = { 0.5f })
}
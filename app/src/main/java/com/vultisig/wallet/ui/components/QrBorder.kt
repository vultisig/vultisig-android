package com.vultisig.wallet.ui.components


import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import com.vultisig.wallet.ui.theme.Theme

@Composable
fun QrBorder(size: Dp, color: Color = Theme.colors.turquoise600Main, borderWidth: Float = 10f) {
    Canvas(modifier = Modifier.size(size)) {
        val segment = this.size.div(5f).height
        val halfSegment = segment.div(2)
        val negSegment = -1 * segment
        val halfNegativeSegment = negSegment.div(2)
        val halfBorderWidth = borderWidth.div(2)
        val zero = 0f
        val quarter = 90f
        drawPath(
            path = Path().apply {
                arcTo(
                    Rect(
                        topLeft = Offset(halfBorderWidth, halfBorderWidth),
                        bottomRight = Offset(segment + halfBorderWidth, segment + halfBorderWidth)
                    ), startAngleDegrees = -180f, sweepAngleDegrees = quarter, forceMoveTo = true
                )
                relativeLineTo(halfSegment - halfBorderWidth, zero)
                relativeMoveTo(segment, zero)
                relativeLineTo(segment, zero)
                relativeMoveTo(segment, zero)
                relativeLineTo(halfSegment, zero)
                arcTo(
                    Rect(
                        topLeft = Offset(4 * segment - halfBorderWidth, halfBorderWidth),
                        bottomRight = Offset(
                            5 * segment - halfBorderWidth, segment + halfBorderWidth
                        )
                    ), startAngleDegrees = -90f, sweepAngleDegrees = quarter, forceMoveTo = true
                )
                relativeLineTo(zero, halfSegment - halfBorderWidth)
                relativeMoveTo(zero, segment)
                relativeLineTo(zero, segment)
                relativeMoveTo(zero, segment)
                relativeLineTo(zero, halfSegment)
                arcTo(
                    Rect(
                        topLeft = Offset(
                            4 * segment - halfBorderWidth, 4 * segment - halfBorderWidth
                        ),
                        bottomRight = Offset(
                            5 * segment - halfBorderWidth, 5 * segment - halfBorderWidth
                        ),
                    ), startAngleDegrees = zero, sweepAngleDegrees = quarter, true
                )
                relativeLineTo(halfNegativeSegment + halfBorderWidth, zero)
                relativeMoveTo(negSegment, zero)
                relativeLineTo(negSegment, zero)
                relativeMoveTo(negSegment, zero)
                relativeLineTo(halfNegativeSegment, zero)
                arcTo(
                    Rect(
                        topLeft = Offset(halfBorderWidth, 4 * segment - halfBorderWidth),
                        bottomRight = Offset(
                            segment + halfBorderWidth, 5 * segment - halfBorderWidth
                        )
                    ), startAngleDegrees = 90f, sweepAngleDegrees = quarter, true
                )
                relativeLineTo(zero, halfNegativeSegment + halfBorderWidth)
                relativeMoveTo(zero, negSegment)
                relativeLineTo(zero, negSegment)
                relativeMoveTo(zero, negSegment)
                relativeLineTo(zero, halfNegativeSegment)
            },
            color = color,
            style = Stroke(width = borderWidth, cap = StrokeCap.Butt)
        )
    }
}
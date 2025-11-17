package com.vultisig.wallet.ui.components.v2.tokenitem

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.vultisig.wallet.ui.components.v2.utils.toPx
import com.vultisig.wallet.ui.theme.Theme

@Composable
internal fun RoundedBorderWithLeaf(
    modifier: Modifier = Modifier,
    borderSize: Dp = 74.dp,
    borderColor: Color = Theme.colors.borders.normal,
    leafColor: Color = Theme.colors.borders.normal,
    checkMarkColor: Color = Theme.colors.alerts.success,
    borderWidth: Dp = 1.5.dp,
    cornerRadius: Dp = 24.dp,
    leafXLength: Dp = 32.dp,
    leafYLength: Dp = 24.dp,
    checkMarkScale: Float = 2f,
    checkMarkWidth: Float = 4f,
    checkMarkOffset: Offset = Offset(-4f, 8f)
) {

    val cornerRadiusPx = cornerRadius.toPx()
    val borderWidthPx = borderWidth.toPx()
    val leafWidthPx = leafXLength.toPx()
    val leafHeighPx = leafYLength.toPx()


    Canvas(
        modifier = modifier.size(borderSize)
    ) {
        val canvasWidth = size.width
        val borderExcludedWidth = canvasWidth - borderWidthPx
        val canvasHeight = size.height
        val borderExcludedHeight = canvasHeight - borderWidthPx


        val borderStartTop = borderWidthPx / 2
        val borderStartLeft = borderWidthPx / 2

        val borderPath = Path().apply {
            addRoundRect(
                RoundRect(
                    left = borderStartLeft,
                    top = borderStartTop,
                    right = borderStartLeft + borderExcludedWidth,
                    bottom = borderStartTop + borderExcludedHeight,
                    cornerRadius = CornerRadius(
                        x = cornerRadiusPx,
                        y = cornerRadiusPx,
                    )
                )
            )
        }


        drawPath(
            path = borderPath,
            color = borderColor,
            style = Stroke(
                width = borderWidthPx,
                cap = StrokeCap.Companion.Round,
                join = StrokeJoin.Companion.Round
            )
        )


        val leafStartTop = borderExcludedHeight - leafHeighPx
        val leafStartLeft = borderExcludedWidth - leafWidthPx
        val leafPath = Path()

        leafPath.op(
            path1 = borderPath,
            path2 = Path().apply {
                addRoundRect(
                    RoundRect(
                        left = leafStartLeft,
                        top = leafStartTop,
                        right = leafStartLeft + borderExcludedWidth,
                        bottom = leafStartTop + borderExcludedHeight,
                        cornerRadius = CornerRadius(
                            x = cornerRadiusPx,
                            y = cornerRadiusPx
                        )
                    )
                )
            },
            operation = PathOperation.Companion.Intersect
        )


        drawPath(
            path = leafPath,
            color = leafColor,
        )

        val checkMarkStartPoint = Offset(
            x = leafPath.getBounds().center.x + checkMarkOffset.x,
            y = leafPath.getBounds().center.y + checkMarkOffset.y
        )

        val checkMarkPath = Path().apply {
            moveTo(
                Offset(
                    x = checkMarkStartPoint.x - 2f * checkMarkScale,
                    y = checkMarkStartPoint.y - 2f * checkMarkScale
                ).x, Offset(
                    x = checkMarkStartPoint.x - 2f * checkMarkScale,
                    y = checkMarkStartPoint.y - 2f * checkMarkScale
                ).y
            )
            lineTo(
                x = checkMarkStartPoint.x,
                y = checkMarkStartPoint.y
            )
            lineTo(
                x = Offset(
                    x = checkMarkStartPoint.x + 5f * checkMarkScale,
                    y = checkMarkStartPoint.y - 6f * checkMarkScale
                ).x,
                y = Offset(
                    x = checkMarkStartPoint.x + 5f * checkMarkScale,
                    y = checkMarkStartPoint.y - 6f * checkMarkScale
                ).y
            )
        }

        drawPath(
            checkMarkPath,
            checkMarkColor,
            style = Stroke(
                width = checkMarkWidth,
                cap = StrokeCap.Companion.Round,
                join = StrokeJoin.Companion.Round
            )
        )
    }
}
package com.vultisig.wallet.ui.components.util

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

enum class CutoutPosition {
    Top,
    Bottom,
    Start,
    End
}

class RoundedWithCutoutShape(
    private val cutoutPosition: CutoutPosition = CutoutPosition.Bottom,
    private val top: Dp = when (cutoutPosition) {
        CutoutPosition.Bottom -> 24.dp
        CutoutPosition.Top, CutoutPosition.Start, CutoutPosition.End -> 12.dp
    },
    private val bottom: Dp = when (cutoutPosition) {
        CutoutPosition.Top -> 24.dp
        CutoutPosition.Bottom, CutoutPosition.Start, CutoutPosition.End -> 12.dp
    },
    private val topStart: Dp = top,
    private val topEnd: Dp = top,
    private val bottomStart: Dp = bottom,
    private val bottomEnd: Dp = bottom,
    private val cutoutRadius: Dp = 32.dp, // 64.dp diameter
    private val cutoutOffsetY: Dp = 0.dp,
    private val cutoutOffsetX: Dp = 0.dp,
    private val onCircleBoundsChanged: ((Offset) -> Unit) = {}
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline = with(density) {
        val path = Path()

        val ts = topStart.toPx()
        val te = topEnd.toPx()
        val bs = bottomStart.toPx()
        val be = bottomEnd.toPx()
        val r = cutoutRadius.toPx()
        val offsetY = cutoutOffsetY.toPx()
        val offsetX = cutoutOffsetX.toPx()

        // Main rounded rectangle
        path.addRoundRect(
            RoundRect(
                rect = Rect(0f, 0f, size.width, size.height),
                topLeft = CornerRadius(ts, ts),
                topRight = CornerRadius(te, te),
                bottomLeft = CornerRadius(bs, bs),
                bottomRight = CornerRadius(be, be)
            )
        )

        val centerX = size.width / 2f
        val centerY = size.height / 2f

        val circleCenter = when (cutoutPosition) {
            CutoutPosition.Top -> Offset(centerX, 0f + offsetY)
            CutoutPosition.Bottom -> Offset(centerX, size.height - offsetY)
            CutoutPosition.Start -> Offset(0f + offsetX, centerY)
            CutoutPosition.End -> Offset(size.width - offsetX, centerY)
        }

        val cutoutPath = Path().apply {
            addOval(Rect(center = circleCenter, radius = r))
        }

        onCircleBoundsChanged(cutoutPath.getBounds().center)

        path.op(path, cutoutPath, PathOperation.Difference)

        Outline.Generic(path)
    }
}
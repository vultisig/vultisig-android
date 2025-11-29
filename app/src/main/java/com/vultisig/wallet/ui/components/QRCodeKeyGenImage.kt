package com.vultisig.wallet.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.createBitmap
import androidx.core.graphics.toColorInt
import com.vultisig.wallet.ui.theme.Theme
import kotlin.math.PI

@Composable
internal fun QRCodeKeyGenImage(
    modifier: Modifier = Modifier,
    bitmapPainter: BitmapPainter? = null,
    cornerRadius: Dp = 16.dp,
    innerPadding: Dp = 24.dp,
    relativePaintedDashLength: Float = 7f,
    relativeEmptyDashLength: Float = 5f,
    countDashesInRect: Int = 4,
    dashesWidth: Float = 8f,
) {
    Box(
        modifier = modifier.drawBehind {
            val cornerRadiusPx = cornerRadius.toPx()
            val rectWidth = size.width - dashesWidth
            val rectHeight = size.height - dashesWidth
            val halfRectLength = rectWidth + rectHeight -
                    ( 2 * cornerRadiusPx - PI.toFloat() * cornerRadiusPx / 2) * 2
            val relativeDashLength = relativePaintedDashLength + relativeEmptyDashLength
            val dashLengthTicker = halfRectLength / ( relativeDashLength * countDashesInRect / 2 )
            val emptyDashLength = dashLengthTicker * relativeEmptyDashLength
            val paintedDashLength = dashLengthTicker * relativePaintedDashLength
            val phase = PI.toFloat() * cornerRadiusPx / 4  + paintedDashLength / 2
            drawRoundRect(
                topLeft = Offset(dashesWidth / 2, dashesWidth / 2),
                size = Size(rectWidth, rectHeight),
                color = Color("#33e6bf".toColorInt()), style = Stroke(
                    width = dashesWidth,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(paintedDashLength, emptyDashLength), phase),
                ),
                cornerRadius = CornerRadius(cornerRadiusPx),
            )
        }
    ) {
        Surface(
            color = Theme.v2.colors.neutrals.n50,
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier
                .padding(innerPadding),
        ) {
            if (bitmapPainter == null) return@Surface
            Image(
                painter = bitmapPainter,
                contentDescription = null,
                contentScale = ContentScale.FillWidth,
                modifier = Modifier
                    .padding(8.dp)
                    .fillMaxWidth(),
            )
        }
    }
}

@Preview
@Composable
private fun QRCodeKeyGenImagePreview() {
    QRCodeKeyGenImage(
        bitmapPainter = BitmapPainter(
            createBitmap(1, 1).asImageBitmap(),
            filterQuality = FilterQuality.None
        )
    )
}
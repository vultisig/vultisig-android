package com.vultisig.wallet.ui.screens.v2.chaintokens.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.theme.Theme


@Composable
internal fun QrContainer(
    chainName: String,
    qrCode: BitmapPainter?,
) {
    val cornerRadius = 24.dp
    val startGradient = Color(0xFF4879FD)
    val endGradient = Color(0xFF0D39B1)
    Box(
        modifier = Modifier
            .width(232.dp)
            .height(271.dp)
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        startGradient,
                        endGradient
                    )
                ),
                shape = RoundedCornerShape(
                    size = cornerRadius
                )
            )
            .drawBehind {
                boxShadow(cornerRadius, startGradient, topOffsetDp = 2.dp, bottomOffsetDp = 3.dp)
            }

            .padding(
                horizontal = 8.dp,
                vertical = 6.dp
            ),
        content = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .clip(
                            shape = RoundedCornerShape(
                                size = cornerRadius
                            )
                        )
                        .background(
                            color = Theme.v2.colors.backgrounds.secondary,
                        )
                ){
                    if(qrCode != null){
                        Image(
                            painter = qrCode,
                            contentDescription = null,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp)
                        )
                    }
                }

                UiSpacer(
                    size = 8.dp
                )

                Text(
                    text = stringResource(
                        R.string.chain_tokens_qr_receive_label,
                        chainName
                    ),
                    style = Theme.brockmann.body.m.medium,
                    color = Theme.v2.colors.text.primary,
                )
            }
        }
    )
}


private fun DrawScope.boxShadow(
    cornerRadius: Dp,
    startGradient: Color,
    topOffsetDp: Dp,
    bottomOffsetDp: Dp,
) {
    val rectWidth = size.width
    val rectHeight = size.height
    val start = 0f
    val topOffset = topOffsetDp.toPx()
    val bottomOffset = bottomOffsetDp.toPx()

    val path1 = Path().apply {
        addRoundRect(
            RoundRect(
                left = start,
                top = start,
                right = rectWidth,
                bottom = rectHeight,
                radiusX = cornerRadius.toPx(),
                radiusY = cornerRadius.toPx()
            )
        )
    }

    val path2 = Path().apply {
        addRoundRect(
            RoundRect(
                left = start,
                top = start,
                right = rectWidth,
                bottom = rectHeight + bottomOffset,
                radiusX = cornerRadius.toPx(),
                radiusY = cornerRadius.toPx()
            )
        )
    }

    val path3 = Path().apply {
        addRoundRect(
            RoundRect(
                left = start,
                top = start - topOffset,
                right = rectWidth,
                bottom = rectHeight,
                radiusX = cornerRadius.toPx(),
                radiusY = cornerRadius.toPx()
            )
        )
    }

    val bottomShadow = Path().apply {
        op(path2, path1, PathOperation.Difference)
    }

    val topShadow = Path().apply {
        op(path3, path1, PathOperation.Difference)
    }

    drawPath(
        path = bottomShadow,
        color = Color(0xFF0a2b84)
    )

    drawPath(
        path = topShadow,
        brush = Brush.verticalGradient(
            colors = listOf(
                Color(0xFF88a8fe),
                startGradient
            ),
            startY = -topOffset,
            endY = start
        )
    )
}

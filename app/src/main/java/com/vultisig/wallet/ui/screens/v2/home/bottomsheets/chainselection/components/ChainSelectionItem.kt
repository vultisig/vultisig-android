package com.vultisig.wallet.ui.screens.v2.home.bottomsheets.chainselection.components

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.logo
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.theme.Theme

@Composable
internal fun ChainSelectionItem(
    modifier: Modifier = Modifier,
    chain: Chain,
    isChecked: Boolean,
    onCheckedChange: (Boolean) -> Unit = {}
) {

    Column(
        modifier = modifier.toggleable(
            value = isChecked,
            onValueChange = onCheckedChange,
        ),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(74.dp)
                .clip(
                    shape = RoundedCornerShape(size = 24.dp)
                )
                .background(
                    color = if (isChecked) Theme.colors.backgrounds.secondary else Theme.colors.backgrounds.disabled
                )
        ) {
            // does not work when use "import ...". maybe ide bug!.
            androidx.compose.animation.AnimatedVisibility(
                visible = isChecked,
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                RoundedBorderWithLeaf()
            }

            Image(
                painter = painterResource(chain.logo),
                contentDescription = null,
                modifier = Modifier.size(28.dp)
            )
        }

        UiSpacer(
            size = 10.dp
        )

        Text(
            text = chain.raw,
            style = Theme.brockmann.supplementary.caption,
            color = Theme.colors.text.primary,
            modifier = Modifier
                .widthIn(max = 74.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }


}

@Composable
internal fun Dp.toPx() = with(LocalDensity.current) {
    toPx()
}

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
                cap = StrokeCap.Round,
                join = StrokeJoin.Round
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
            operation = PathOperation.Intersect
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
                cap = StrokeCap.Round,
                join = StrokeJoin.Round
            )
        )
    }
}

@Preview
@Composable
fun PreviewRChainSelectionItem() {
    ChainSelectionItem(
        chain = Chain.Bitcoin,
        isChecked = true
    )
}

@Preview
@Composable
fun PreviewChainSelectionItem2() {
    ChainSelectionItem(
        chain = Chain.Bitcoin,
        isChecked = false
    )
}


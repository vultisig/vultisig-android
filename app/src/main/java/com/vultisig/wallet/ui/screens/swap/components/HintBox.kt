package com.vultisig.wallet.ui.screens.swap.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.PaintingStyle
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.UiIcon
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.theme.Theme

@Composable
private fun PointerShapeUp(
    modifier: Modifier = Modifier,
    shapeSize: DpSize = DpSize(width = 40.dp, height = 15.dp),
    shapeColor: Color,
) {
    Canvas(modifier = modifier.width(shapeSize.width).height(shapeSize.height)) {
        val path =
            Path().apply {
                val maxWidth = size.width
                val maxHeight = size.height + 1
                val offSet = 30f

                moveTo(0f, maxHeight)
                relativeLineTo(offSet, 0f)
                lineTo(maxWidth.div(2), 0f)
                lineTo(maxWidth - offSet, maxHeight)
                relativeLineTo(offSet, 0f)
                close()
            }

        val paint =
            Paint().apply {
                color = shapeColor
                style = PaintingStyle.Fill
                pathEffect = PathEffect.cornerPathEffect(20f)
            }

        drawIntoCanvas { canvas -> canvas.drawPath(path, paint) }
    }
}

@Composable
private fun PointerShapeDown(
    modifier: Modifier = Modifier,
    shapeSize: DpSize = DpSize(width = 40.dp, height = 15.dp),
    shapeColor: Color,
) {
    Canvas(modifier = modifier.width(shapeSize.width).height(shapeSize.height)) {
        val path =
            Path().apply {
                val maxWidth = size.width
                val maxHeight = size.height + 1
                val offSet = 30f

                moveTo(0f, 0f)
                relativeLineTo(offSet, 0f)
                lineTo(maxWidth.div(2), maxHeight)
                lineTo(maxWidth - offSet, 0f)
                relativeLineTo(offSet, 0f)
                close()
            }

        val paint =
            Paint().apply {
                color = shapeColor
                style = PaintingStyle.Fill
                pathEffect = PathEffect.cornerPathEffect(20f)
            }

        drawIntoCanvas { canvas -> canvas.drawPath(path, paint) }
    }
}

@Composable
internal fun HintBox(
    modifier: Modifier = Modifier,
    isVisible: Boolean,
    isPointerTriangleOnTop: Boolean = true,
    title: String,
    message: String,
    offset: IntOffset,
    pointerAlignment: Alignment.Horizontal = Alignment.CenterHorizontally,
    pointerOffset: DpOffset = DpOffset.Zero,
    onDismissClick: () -> Unit,
) {
    AnimatedVisibility(visible = isVisible, enter = fadeIn(), exit = fadeOut()) {
        Popup(offset = offset, onDismissRequest = null) {
            HintBoxPopupContent(
                modifier = modifier,
                title = title,
                message = message,
                isPointerOnTop = isPointerTriangleOnTop,
                pointerAlignment = pointerAlignment,
                pointerOffset = pointerOffset,
                onDismissClick = onDismissClick,
            )
        }
    }
}

@Composable
private fun HintBoxPopupContent(
    modifier: Modifier = Modifier,
    title: String,
    message: String,
    isPointerOnTop: Boolean,
    pointerAlignment: Alignment.Horizontal = Alignment.CenterHorizontally,
    pointerOffset: DpOffset = DpOffset.Zero,
    onDismissClick: () -> Unit,
) {

    val shapeColor = Theme.v2.colors.neutrals.n200
    Column(modifier = modifier.clickable(onClick = onDismissClick)) {
        if (isPointerOnTop) {
            PointerShapeUp(
                shapeColor = shapeColor,
                modifier =
                    Modifier.align(alignment = pointerAlignment)
                        .offset(x = pointerOffset.x, y = pointerOffset.y),
            )
        }
        Column(
            modifier =
                Modifier.fillMaxWidth()
                    .background(
                        color = shapeColor,
                        shape =
                            RoundedCornerShape(
                                topStart = 16.dp,
                                topEnd = 4.dp,
                                bottomStart = 16.dp,
                                bottomEnd = 16.dp,
                            ),
                    )
                    .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            if (title.isNotEmpty()) {
                Row {
                    Text(
                        text = title,
                        style = Theme.brockmann.body.m.medium,
                        color = Theme.v2.colors.text.inverse,
                    )
                    UiSpacer(weight = 1f)
                    UiIcon(
                        drawableResId = R.drawable.x,
                        size = 16.dp,
                        tint = Theme.v2.colors.text.button.disabled,
                    )
                }

                UiSpacer(size = 2.dp)
            }

            Text(
                text = message,
                color = Theme.v2.colors.text.tertiary,
                style = Theme.brockmann.supplementary.footnote,
            )
        }

        if (isPointerOnTop.not()) {
            PointerShapeDown(
                shapeColor = shapeColor,
                modifier =
                    Modifier.align(alignment = pointerAlignment)
                        .offset(x = pointerOffset.x, y = pointerOffset.y),
            )
        }
    }
}

@Preview
@Composable
private fun HintBoxTopPointerPreview() {
    // In preview mode, the popup displays an unwanted background that's not visible in the actual
    // app.
    HintBox(
        modifier = Modifier.width(250.dp),
        title = "Insufficient funds",
        message = "Insufficient funds to execute the swap. Please fund the wallet.",
        onDismissClick = {},
        offset = IntOffset.Zero,
        isVisible = true,
    )
}

@Preview
@Composable
private fun HintBoxDownPointerPreview() {
    // In preview mode, the popup displays an unwanted background that's not visible in the actual
    // app.
    HintBox(
        modifier = Modifier.width(250.dp),
        title = "Insufficient funds",
        message = "Insufficient funds to execute the swap. Please fund the wallet.",
        onDismissClick = {},
        offset = IntOffset.Zero,
        isVisible = true,
        isPointerTriangleOnTop = false,
    )
}

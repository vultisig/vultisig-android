package com.vultisig.wallet.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.theme.Theme

@Composable
internal fun MoreInfoBox(
    text: String,
    title: String,
    modifier: Modifier = Modifier,
) {
    val textMeasurer = rememberTextMeasurer()
    val titleTextStyle =
        Theme.brockmann.body.s.medium.copy(
            color = Theme.colors.text.button.dark
        )
    val hintTextStyle =
        Theme.brockmann.supplementary.footnote.copy(
            color = Theme.colors.text.extraLight
        )
    val closeIcon = painterResource(R.drawable.x)
    val closeIconTint = Theme.colors.text.button.disabled
    val backgroundColor = Theme.colors.neutrals.n200
    val fontSize = hintTextStyle.fontSize.value

    Canvas(
        modifier = modifier
            .fillMaxSize()
    ) {
        val canvasWidth = size.width
        val backgroundPadding = 16.dp.toPx()
        var maxWidth = size.width - (2 * backgroundPadding)
        val lineHeight = 40f
        val spaceBetweenTitleAndHint = 10
        val lines = mutableListOf<String>()
        val pointerHeight = 50f
        val closeIconSize = 16.dp.toPx()
        val cornerRadius = 12.dp.toPx()
        val measurementTitle = textMeasurer.measure(
            text = title,
            style = titleTextStyle,
            overflow = TextOverflow.Clip
        )
        val titleHeight = measurementTitle.size.height
        var totalHeight = 0f
        val startBoxFromX = 20.dp.toPx()
        var currentLine = StringBuilder()
        text.split(" ").forEach { word ->
            val testLine = if (currentLine.isEmpty()) word else "$currentLine $word"
            val measurementHint = textMeasurer.measure(
                text = testLine,
                style = hintTextStyle,
                overflow = TextOverflow.Clip
            )

            if (measurementHint.size.width <= canvasWidth - (backgroundPadding * 2) - startBoxFromX) {
                currentLine.append(if (currentLine.isEmpty()) word else " $word")
            } else {
                if (currentLine.isNotEmpty()) {
                    val lineWidth =
                        textMeasurer.measure(
                            currentLine.toString(),
                            style = hintTextStyle
                        ).size.width
                    maxWidth = maxWidth.coerceAtLeast(lineWidth.toFloat())
                    lines.add(currentLine.toString())
                    totalHeight += fontSize + lineHeight
                    currentLine = StringBuilder(word)
                }
            }
        }

        if (currentLine.isNotEmpty()) {
            val lineWidth =
                textMeasurer.measure(currentLine.toString(), style = hintTextStyle).size.width
            maxWidth = maxWidth.coerceAtLeast(lineWidth.toFloat())
            lines.add(currentLine.toString())
            totalHeight += fontSize + lineHeight
        }
        val startPointerX = size.width - 2 * pointerHeight - backgroundPadding

        val backgroundPath = Path().apply {
            addRoundRect(
                RoundRect(
                    left = startBoxFromX,
                    top = pointerHeight,
                    right = maxWidth + (backgroundPadding * 2),
                    bottom = totalHeight + titleHeight + pointerHeight + (backgroundPadding * 2),
                    cornerRadius = CornerRadius(cornerRadius)
                )
            )
            moveTo(0f + startPointerX, pointerHeight)
            lineTo(pointerHeight + startPointerX, 0f)
            lineTo(2 * pointerHeight + startPointerX, pointerHeight)
            close()
        }
        drawPath(backgroundPath, color = backgroundColor)
        drawText(
            textMeasurer = textMeasurer,
            text = title,
            style = titleTextStyle,
            topLeft = Offset(
                x = backgroundPadding + startBoxFromX,
                y = pointerHeight + backgroundPadding
            )
        )
        var currentY =
            backgroundPadding + pointerHeight + titleHeight + spaceBetweenTitleAndHint
        lines.forEach { line ->
            drawText(
                textMeasurer = textMeasurer,
                text = line,
                style = hintTextStyle,
                topLeft = Offset(backgroundPadding + startBoxFromX, currentY)
            )
            currentY += fontSize + lineHeight
        }

        translate(
            left = maxWidth,
            top = pointerHeight - closeIconSize / 2 + backgroundPadding + titleHeight / 2
        ) {
            with(closeIcon) {
                draw(
                    size = Size(closeIconSize, closeIconSize),
                    colorFilter = ColorFilter.tint(color = closeIconTint),
                )
            }
        }
    }
}
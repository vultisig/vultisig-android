package com.vultisig.wallet.ui.components.buttons

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp


@Composable
internal fun AutoSizingText(
    text: String,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
    minFontSize: TextUnit = 1.sp,
) {

    SubcomposeLayout(modifier = modifier) { constraints ->
        val maxWidth = constraints.maxWidth.toFloat()

        if (maxWidth <= 0) {
            return@SubcomposeLayout layout(0, 0) {}
        }

        // First, try to measure at original font size
        val originalMeasurable = subcompose("originalText") {
            Text(
                text = text,
                style = style,
                color = color,
                maxLines = 1,
                softWrap = false,
            )
        }

        val originalPlaceables = originalMeasurable.map {
            it.measure(Constraints(maxWidth = Constraints.Infinity))
        }
        val originalWidth = originalPlaceables.firstOrNull()?.width ?: 0

        // If it fits, use original size
        if (originalWidth <= maxWidth) {
            val finalPlaceable = originalPlaceables.firstOrNull()
            if (finalPlaceable != null) {
                layout(finalPlaceable.width, finalPlaceable.height) {
                    finalPlaceable.place(0, 0)
                }
            } else {
                layout(0, 0) {}
            }
        }

        // Otherwise, binary search for best fit
        var low = minFontSize.value
        var high = style.fontSize.value
        var bestStyle = style

        repeat(10) { iteration ->
            val mid = (low + high) / 2
            val testStyle = style.copy(fontSize = mid.sp)

            val measurable = subcompose("text_$iteration") {
                Text(
                    text = text,
                    style = testStyle,
                    color = color,
                    maxLines = 1,
                    softWrap = false,
                )
            }

            val placeables = measurable.map {
                it.measure(Constraints(maxWidth = Constraints.Infinity))
            }
            val textWidth = placeables.firstOrNull()?.width ?: 0

            if (textWidth <= maxWidth) {
                bestStyle = testStyle
                low = mid
            } else {
                high = mid
            }

            if ((high - low) < 0.5f) return@repeat
        }

        val finalMeasurable = subcompose("finalText") {
            Text(
                text = text,
                style = bestStyle,
                color = color,
                maxLines = 1,
                softWrap = false,
            )
        }

        val placeables = finalMeasurable.map {
            it.measure(Constraints(maxWidth = maxWidth.toInt()))
        }
        val placeable = placeables.firstOrNull()

        if (placeable != null) {
            layout(placeable.width, placeable.height) {
                placeable.place(0, 0)
            }
        } else {
            layout(0, 0) {}
        }
    }
}
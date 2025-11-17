package com.vultisig.wallet.ui.components.util

import androidx.annotation.StringRes
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle

@Composable
internal fun SequenceOfGradientText(
    modifier: Modifier = Modifier,
    listTextItems: List<PartiallyGradientTextItem>,
    style: TextStyle = LocalTextStyle.current,
    textAlign: TextAlign = TextAlign.Center
) {
    val annotatedText = buildAnnotatedString {
        listTextItems.forEachIndexed{ index, textItem ->
            when (textItem.coloring) {
                is GradientColoring.VsColor -> {
                    withStyle(style = SpanStyle(color = textItem.coloring.color)) {
                        append(stringResource(textItem.resId, textItem.formatArgs))
                    }
                }
                is GradientColoring.Gradient -> {
                    withStyle(
                        style = SpanStyle(
                            brush = textItem.coloring.brush,
                        )
                    ) {
                        append(stringResource(textItem.resId, textItem.formatArgs))
                    }
                }
            }
            if (index < listTextItems.size - 1) {
                append(" ")
            }
        }
    }
    Text(
        text = annotatedText,
        style = style,
        textAlign = textAlign,
        modifier = modifier,
    )
}

internal class PartiallyGradientTextItem(
    @StringRes val resId: Int,
    val formatArgs: Any = emptyArray<Any>(),
    val coloring: GradientColoring,
)

internal sealed class GradientColoring {
    data class VsColor(val color: Color) : GradientColoring()
    data class Gradient(val brush: Brush) : GradientColoring()
}
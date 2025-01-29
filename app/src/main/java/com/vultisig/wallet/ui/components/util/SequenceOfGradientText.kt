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
    listTextItems: List<PartiallyGradientTextItem>,
    style: TextStyle = LocalTextStyle.current,
    textAlign: TextAlign = TextAlign.Center,
    modifier: Modifier = Modifier,
) {
    val annotatedText = buildAnnotatedString {
        listTextItems.forEachIndexed{ index, textItem ->
            when (textItem.gradientColors.size) {
                0 -> {}
                1 -> {
                    withStyle(style = SpanStyle(color = textItem.gradientColors[0])) {
                        append(stringResource(textItem.resId))
                    }
                }
                else -> {
                    withStyle(
                        style = SpanStyle(
                            brush = Brush.horizontalGradient(textItem.gradientColors),
                        )
                    ) {
                        append(stringResource(textItem.resId))
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
    val gradientColors: List<Color>,
)
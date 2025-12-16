package com.vultisig.wallet.ui.components.v2.texts

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import com.vultisig.wallet.ui.components.ToggleVisibilityText
import com.vultisig.wallet.ui.theme.Theme

@Composable
internal fun LoadableValue(
    value: String?,
    isVisible: Boolean,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontStyle: FontStyle? = null,
    fontWeight: FontWeight? = null,
    fontFamily: FontFamily? = null,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    textDecoration: TextDecoration? = null,
    textAlign: TextAlign? = null,
    lineHeight: TextUnit = TextUnit.Unspecified,
    overflow: TextOverflow = TextOverflow.Clip,
    softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
    minLines: Int = 1,
    onTextLayout: ((TextLayoutResult) -> Unit)? = null,
) {
    AnimatedContent(
        modifier = modifier,
        targetState = value,
    ) { v ->
        ToggleVisibilityText(
            text = v ?: "",
            isVisible = isVisible,
            style = style,
            color = color,
            maxLines = maxLines,
            modifier = modifier
                .clip(RoundedCornerShape(2.dp))
                .background(Theme.v2.colors.backgrounds.tertiary_2)
                .defaultMinSize(minWidth = 20.dp, minHeight = 16.dp).takeIf {
                    v == null
                } ?: modifier,
            fontSize = fontSize,
            fontStyle = fontStyle,
            fontWeight = fontWeight,
            fontFamily = fontFamily,
            letterSpacing = letterSpacing,
            textDecoration = textDecoration,
            textAlign = textAlign,
            lineHeight = lineHeight,
            overflow = overflow,
            softWrap = softWrap,
            minLines = minLines,
            onTextLayout = onTextLayout,
        )
    }
}
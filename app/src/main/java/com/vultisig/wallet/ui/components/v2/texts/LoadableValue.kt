package com.vultisig.wallet.ui.components.v2.texts

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.vultisig.wallet.ui.components.ToggleVisibilityText
import com.vultisig.wallet.ui.components.library.UiPlaceholderLoader

@Composable
internal fun LoadableValue(
    value: String?,
    isVisible: Boolean,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
    maxLines: Int = Int.MAX_VALUE,
) {
    AnimatedContent(
        modifier = modifier,
        targetState = value,
    ) { v ->
        if (v != null) {
            ToggleVisibilityText(
                text = v,
                isVisible = isVisible,
                style = style,
                color = color,
                maxLines = maxLines
            )
        } else {
            val fontHeight = with(LocalDensity.current) {
                style.fontSize.toDp()
            }
            UiPlaceholderLoader(
                modifier = Modifier
                    .width(48.dp)
                    .height(fontHeight),
            )
        }
    }
}
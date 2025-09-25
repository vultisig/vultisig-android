package com.vultisig.wallet.ui.components.v2.texts

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
            )
        } else {
            UiPlaceholderLoader(
                modifier = Modifier.Companion
                    .width(48.dp)
                    .height(16.dp),
            )
        }
    }
}
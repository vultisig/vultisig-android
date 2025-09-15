package com.vultisig.wallet.ui.screens.v2.home.components

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
internal fun AnimatedPrice(
    totalFiatValue: String?,
    isVisible: Boolean,
    style: TextStyle,
    color: Color,
) {
    AnimatedContent(
        targetState = totalFiatValue,
        label = "ChainAccount FiatAmount",
    ) { totalFiatValue ->
        if (totalFiatValue != null) {
            ToggleVisibilityText(
                text = totalFiatValue,
                isVisible = isVisible,
                style = style,
                color = color,
            )
        } else {
            UiPlaceholderLoader(
                modifier = Modifier.Companion
                    .width(48.dp)
                    .height(32.dp),
            )
        }
    }
}
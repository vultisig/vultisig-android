package com.vultisig.wallet.ui.components.v3

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp


@Composable
fun <T> AnimatedItem(
    modifier: Modifier = Modifier,
    current: T,
    items: List<T>,
    style: TextStyle,
    color: Color,
    width: Dp = 100.dp,
    formatter: (T) -> String = { it.toString() },
) { 

    AnimatedContent(
        targetState = current,
        modifier = modifier,
        transitionSpec = {
            val initialIndex = items.indexOf(initialState)
            val targetIndex = items.indexOf(targetState)
            if (targetIndex > initialIndex) {
                slideUp()
            } else {
                slideDown()
            }
        },
        label = "AnimateItemInList"
    ) { displayedItem ->
        Text(
            text = formatter(displayedItem),
            style = style,
            color = color,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .width(width)
        )
    }
}

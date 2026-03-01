package com.vultisig.wallet.ui.components.v3

import androidx.compose.animation.AnimatedContent
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier


class VerticalAnimatedPagerScope {
    val items = mutableListOf<@Composable () -> Unit>()

    fun item(content: @Composable () -> Unit) {
        items.add(content)
    }
}

@Composable
fun VerticalAnimatedPager(
    modifier: Modifier = Modifier,
    index: Int,
    content: VerticalAnimatedPagerScope.() -> Unit,
) {
    val scope = VerticalAnimatedPagerScope().apply(content)
    val items = scope.items

    AnimatedContent(
        targetState = index,
        modifier = modifier,
        transitionSpec = {
            if (targetState > initialState) slideUp() else slideDown()
        },
        label = "VerticalAnimatedPager"
    ) { currentIndex ->
        items.getOrNull(currentIndex)?.invoke()
    }
}
package com.vultisig.wallet.ui.components.v3

import androidx.compose.animation.AnimatedContent
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier


class HorizontalAnimatedPagerScope {
    val items = mutableListOf<@Composable () -> Unit>()

    fun item(content: @Composable () -> Unit) {
        items.add(content)
    }
}

@Composable
fun HorizontalAnimatedPager(
    modifier: Modifier = Modifier,
    index: Int,
    content: HorizontalAnimatedPagerScope.() -> Unit,
) {
    val scope = HorizontalAnimatedPagerScope().apply(content)
    val items = scope.items

    AnimatedContent(
        targetState = index,
        modifier = modifier,
        transitionSpec = {
            if (targetState > initialState) slideRight() else slideLeft()
        },
        label = "HorizontalAnimatedPager"
    ) { currentIndex ->
        items.getOrNull(currentIndex)?.invoke()
    }
}
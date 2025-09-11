package com.vultisig.wallet.ui.components.v2.scaffold

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow


@Composable
internal fun rememberExpandableTopbarScrollState(
    lazyListState: LazyListState,
    scrollBehavior: ExpandableTopbarScrollBehavior = ExpandableTopbarScrollBehavior.EXPAND_WHEN_SCROLLING_DOWN
): State<Boolean> {

    val isScrollingDown = remember { mutableStateOf(false) }

    var lastIndex by remember { mutableIntStateOf(0) }
    var lastOffset by remember { mutableIntStateOf(0) }

    LaunchedEffect(lazyListState) {
        snapshotFlow {
            lazyListState.firstVisibleItemIndex to lazyListState.firstVisibleItemScrollOffset
        }.collect { (index, offset) ->
            isScrollingDown.value = when {
                index > lastIndex -> true // Scrolled to next item
                index < lastIndex -> false // Scrolled to previous item
                else -> offset > lastOffset // Same item, compare offset
            }

            lastIndex = index
            lastOffset = offset
        }
    }


    val isFirstItemVisible = remember {
        derivedStateOf { lazyListState.firstVisibleItemIndex > 0 }
    }

    return when (scrollBehavior) {
        ExpandableTopbarScrollBehavior.EXPAND_WHEN_FIRST_ITEM_VISIBLE -> isFirstItemVisible
        ExpandableTopbarScrollBehavior.EXPAND_WHEN_SCROLLING_DOWN -> isScrollingDown
    }
}



internal enum class ExpandableTopbarScrollBehavior {
    EXPAND_WHEN_FIRST_ITEM_VISIBLE,
    EXPAND_WHEN_SCROLLING_DOWN,
}

package com.vultisig.wallet.ui.components.reorderable

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.dp
import com.vultisig.wallet.ui.components.reorderable.utils.ReorderableItem
import com.vultisig.wallet.ui.components.reorderable.utils.rememberReorderableLazyListState



@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun <T : Any> VerticalReorderList(
    modifier: Modifier = Modifier,
    isReorderEnabled: Boolean = true,
    data: List<T>,
    key: (item: T) -> Any,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    onMove: (from: Int, to: Int) -> Unit,
    beforeContents: List<@Composable LazyItemScope.() -> Unit>? = null,
    afterContents: List<@Composable LazyItemScope.() -> Unit>? = null,
    content: @Composable (item: T) -> Unit,
) {
    val isDraggingEnabled by rememberUpdatedState(newValue = isReorderEnabled)
    val lazyListState = rememberLazyListState()
    val state = rememberReorderableLazyListState(
        lazyListState = lazyListState,
        onMove = { from, to ->
            val i = from.index + (beforeContents?.let { it.lastIndex - 1 } ?: 0)
            val j = to.index + (beforeContents?.let { it.lastIndex - 1 } ?: 0)

            onMove(i, j)
        })
    LazyColumn(
        verticalArrangement = verticalArrangement,
        state = lazyListState,
        contentPadding = contentPadding,
        modifier = modifier
    ) {
        beforeContents?.forEach { content ->
            item(content = content)
        }
        items(data, key) { item ->
            ReorderableItem(state, key = key(item)) { isDragging ->
                val elevation =
                    animateDpAsState(if (isDragging) 16.dp else 0.dp, label = "elevation")
                Column(
                    modifier = Modifier
                        .longPressDraggableHandle(enabled = isDraggingEnabled)
                        .shadow(elevation.value)
                ) {
                    content(item)
                }
            }
        }
        afterContents?.forEach { content ->
            item(content = content)
        }
    }
}
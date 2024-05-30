package com.vultisig.wallet.ui.components.reorderable

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.dp
import com.vultisig.wallet.ui.components.reorderable.utils.ReorderableItem
import com.vultisig.wallet.ui.components.reorderable.utils.detectReorderAfterLongPress
import com.vultisig.wallet.ui.components.reorderable.utils.rememberReorderableLazyListState
import com.vultisig.wallet.ui.components.reorderable.utils.reorderable


@Composable
internal fun <T : Any> VerticalReorderList(
    modifier: Modifier = Modifier,
    data: List<T>,
    key: (item: T) -> Any = { it },
    contentPadding: PaddingValues = PaddingValues(0.dp),
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    onMove: (from: Int, to: Int) -> Unit,
    beforeContents: List<@Composable LazyItemScope.() -> Unit>? = null,
    afterContents: List<@Composable LazyItemScope.() -> Unit>? = null,
    content: @Composable (item: T) -> Unit,
) {
    val dataSize by rememberUpdatedState(newValue = data.size)
    val state = rememberReorderableLazyListState(onMove = { from, to ->
        val i = from.index + (beforeContents?.let { it.lastIndex - 1 } ?: 0)
        val j = to.index + (beforeContents?.let { it.lastIndex - 1 } ?: 0)
        val boundaries = listOf(-1, dataSize + (beforeContents?.lastIndex ?: 0))
        if (boundaries.any { it == i || it == j })
            return@rememberReorderableLazyListState
        onMove(i, j)
    })
    LazyColumn(
        verticalArrangement = verticalArrangement,
        state = state.listState,
        contentPadding = contentPadding,
        modifier = modifier
            .reorderable(state)
            .detectReorderAfterLongPress(state)
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
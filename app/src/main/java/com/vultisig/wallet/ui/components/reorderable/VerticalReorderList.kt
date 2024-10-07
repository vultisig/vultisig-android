package com.vultisig.wallet.ui.components.reorderable

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.dp
import com.vultisig.wallet.ui.components.reorderable.utils.ReorderableItem
import com.vultisig.wallet.ui.components.reorderable.utils.ReorderableLazyListState
import com.vultisig.wallet.ui.components.reorderable.utils.rememberReorderableLazyListState


@Composable
internal fun <T: Any ,R : Any> VerticalDoubleReorderList(
    modifier: Modifier = Modifier,
    isReorderEnabled: Boolean = true,
    dataT: List<T>,
    dataR: List<R>,
    keyT: (item: T) -> Any,
    keyR: (item: R) -> Any,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    onMoveT: (from: Int, to: Int) -> Unit,
    onMoveR: (from: Int, to: Int) -> Unit,
    beforeContents: List<@Composable LazyItemScope.() -> Unit>? = null,
    midContents: List<@Composable LazyItemScope.() -> Unit>? = null,
    afterContents: List<@Composable LazyItemScope.() -> Unit>? = null,
    contentT: @Composable (item: T) -> Unit,
    contentR: @Composable (item: R) -> Unit,
) {
    val isDraggingEnabled by rememberUpdatedState(newValue = isReorderEnabled)
    val lazyListState = rememberLazyListState()
    val stateT = rememberReorderableLazyListState(
        lazyListState = lazyListState,
        onMove = { from, to ->
            val before = beforeContents?.size?: 0
            val i = from.index - before
            val j = to.index - before
            onMoveT(i, j)
        })
    val stateR = rememberReorderableLazyListState(
        lazyListState = lazyListState,
        onMove = { from, to ->
            val before =
                if (dataT.isEmpty())
                    midContents?.size?: 0
                else
                    (beforeContents?.size?: 0) + dataT.size + (midContents?.size?: 0)
            val i = from.index - before
            val j = to.index - before
            onMoveR(i, j)
        })
    LazyColumn(
        verticalArrangement = verticalArrangement,
        state = lazyListState,
        contentPadding = contentPadding,
        modifier = modifier
    ) {
        if (dataT.isNotEmpty()) {
            beforeContents?.forEach { content ->
                item(content = content)
            }
            reorderableList(
                data = dataT,
                key = keyT,
                state = stateT,
                isDraggingEnabled = isDraggingEnabled,
                content = contentT,
            )
        }

        if (dataT.isNotEmpty() && dataR.isNotEmpty()) {
            midContents?.forEach { content ->
                item(content = content)
            }
            reorderableList(
                data = dataR,
                key = keyR,
                state = stateR,
                isDraggingEnabled = isDraggingEnabled,
                content = contentR,
            )
        }

        afterContents?.forEach { content ->
            item(content = content)
        }
    }
}

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
        reorderableList(
            data = data,
            key = key,
            state = state,
            isDraggingEnabled = isDraggingEnabled,
            content = content,
        )
        afterContents?.forEach { content ->
            item(content = content)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
private fun <T : Any> LazyListScope.reorderableList(
    data: List<T>,
    key: (item: T) -> Any,
    state: ReorderableLazyListState,
    isDraggingEnabled: Boolean,
    content: @Composable (item: T) -> Unit,
) {
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
}
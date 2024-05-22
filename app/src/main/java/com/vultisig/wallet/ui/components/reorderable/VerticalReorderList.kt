package com.vultisig.wallet.ui.components.reorderable

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.dp
import com.vultisig.wallet.ui.components.reorderable.utils.ItemPosition
import com.vultisig.wallet.ui.components.reorderable.utils.ReorderableItem
import com.vultisig.wallet.ui.components.reorderable.utils.detectReorderAfterLongPress
import com.vultisig.wallet.ui.components.reorderable.utils.rememberReorderableLazyListState
import com.vultisig.wallet.ui.components.reorderable.utils.reorderable


@Composable
internal fun <T : Any> VerticalReorderList(
    modifier: Modifier = Modifier,
    data: List<T>,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    onMove: (from: ItemPosition, to: ItemPosition) -> Unit,
    content: @Composable (item: T) -> Unit
) {
    val state = rememberReorderableLazyListState(onMove = onMove)
    LazyColumn(
        state = state.listState,
        contentPadding = contentPadding,
        modifier = modifier
            .reorderable(state)
            .detectReorderAfterLongPress(state)
    ) {
        items(data, { it }) { item ->
            ReorderableItem(state, key = item) { isDragging ->
                val elevation = animateDpAsState(if (isDragging) 16.dp else 0.dp, label = "elevation")
                Column(
                    modifier = Modifier
                        .shadow(elevation.value)
                ) {
                    content(item)
                }
            }
        }
    }
}
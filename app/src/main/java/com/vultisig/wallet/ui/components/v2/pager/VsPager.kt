package com.vultisig.wallet.ui.components.v2.pager

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import com.vultisig.wallet.ui.components.v2.pager.utils.VsPagerState

@Composable
internal fun VsPager(
    modifier: Modifier = Modifier,
    state: VsPagerState,
    content: VsPagerState.() -> Unit,
) {
    var maxHeight by remember { mutableStateOf(140.dp) }
    val density = LocalDensity.current

    val pagerState = rememberPagerState(
        pageCount = { state.pageCount }
    )

    LaunchedEffect(state) {
        state.clear()
        state.content()
    }


    LaunchedEffect(pagerState.currentPage) {
        state.updateCurrentPage(pagerState.currentPage)
    }

    val pageHeights = remember { mutableStateMapOf<Int, Dp>() }

    val updateMaxHeight = remember {
        { index: Int, newHeight: Dp ->
            val oldHeight = pageHeights[index]
            if (oldHeight != newHeight) {
                pageHeights[index] = newHeight
                maxHeight = max(newHeight, maxHeight)
            }
        }
    }

    val onPageMeasured = remember(density, updateMaxHeight) {
        { index: Int, size: IntSize ->
            val heightDp = with(density) { size.height.toDp() }
            updateMaxHeight(index, heightDp)
        }
    }

    HorizontalPager(
        state = pagerState,
        modifier = modifier
            .heightIn(min = maxHeight),
        key = { it },
        pageSpacing = 8.dp,
    ) { index ->
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .onGloballyPositioned { coordinates ->
                    onPageMeasured(index, coordinates.size)
                }
        ) {
            state.pages[index]()
        }
    }
}

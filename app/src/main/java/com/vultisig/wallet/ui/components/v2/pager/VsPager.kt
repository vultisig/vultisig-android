package com.vultisig.wallet.ui.components.v2.pager

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import com.vultisig.wallet.ui.components.v2.pager.utils.VsPagerState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp

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

    val onMeasure: (IntSize) -> Unit = remember {
        { coordinates ->
            val heightDp = with(density) { coordinates.height.toDp() }
            if (heightDp > maxHeight) {
                maxHeight = heightDp
            }
        }
    }

    HorizontalPager(
        state = pagerState,
        modifier = modifier.height(maxHeight),
        key = { it },
        pageSpacing = 8.dp,
    ) { index ->
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .onGloballyPositioned { coordinates ->
                    val size = coordinates.size
                    if (pagerState.currentPage == index) {
                        onMeasure(size)
                    }
                }
        ) {
            state.pages[index]()
        }
    }
}

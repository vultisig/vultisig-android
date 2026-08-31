package com.vultisig.wallet.ui.components.v2.pager

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.vultisig.wallet.ui.components.v2.pager.utils.VsPagerState

@Composable
internal fun VsPager(
    modifier: Modifier = Modifier,
    state: VsPagerState,
    content: VsPagerState.() -> Unit,
) {
    val density = LocalDensity.current

    val pagerState = rememberPagerState(pageCount = { state.pageCount })

    val pageHeights = remember { mutableStateMapOf<Int, Dp>() }

    LaunchedEffect(state) {
        state.clear()
        state.content()
        // Page indices are reused across a different set of pages, so heights measured for the
        // previous set would otherwise keep the pager at the tallest page it has *ever* shown.
        pageHeights.clear()
    }

    LaunchedEffect(pagerState.currentPage) { state.updateCurrentPage(pagerState.currentPage) }

    // Tallest page currently measured, so the pager reserves one height for every page instead of
    // resizing as the user swipes. Derived rather than accumulated: a monotonic maximum could only
    // ever grow, and would strand the empty space of a page that is no longer there.
    val maxHeight by remember { derivedStateOf { pageHeights.values.maxOrNull() ?: 0.dp } }

    val onPageMeasured =
        remember(density) {
            { index: Int, size: IntSize ->
                pageHeights[index] = with(density) { size.height.toDp() }
            }
        }

    HorizontalPager(
        state = pagerState,
        modifier = modifier.heightIn(min = maxHeight),
        key = { it },
        pageSpacing = 8.dp,
    ) { index ->
        Box(
            modifier =
                Modifier.fillMaxWidth().onGloballyPositioned { coordinates ->
                    onPageMeasured(index, coordinates.size)
                }
        ) {
            state.pages[index]()
        }
    }
}

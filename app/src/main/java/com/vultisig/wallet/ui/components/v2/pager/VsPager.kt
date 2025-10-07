package com.vultisig.wallet.ui.components.v2.pager

import com.vultisig.wallet.ui.components.v2.pager.utils.VsPagerState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp


@Composable
internal fun VsPager(
    modifier: Modifier = Modifier,
    state: VsPagerState,
    content: VsPagerState.() -> Unit,
) {

    LaunchedEffect(state) {
        state.clear()
        state.content()
    }

    val pagerState = rememberPagerState(
        pageCount = { state.pageCount }
    )

    LaunchedEffect(pagerState.currentPage) {
        state.updateCurrentPage(pagerState.currentPage)
    }

    HorizontalPager(
        state = pagerState,
        modifier = modifier,
        key = { it },
        pageSpacing = 8.dp,
    ) { index ->
        state.pages[index]()
    }
}

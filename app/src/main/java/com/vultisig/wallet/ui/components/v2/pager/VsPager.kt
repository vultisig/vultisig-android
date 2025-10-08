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
import androidx.compose.ui.unit.dp

@Composable
internal fun VsPager(
    modifier: Modifier = Modifier,
    state: VsPagerState,
    content: VsPagerState.() -> Unit,
) {
    var maxHeight by remember { mutableStateOf(0.dp) }
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

    HorizontalPager(
        state = pagerState,
        modifier = modifier.then(
            if (maxHeight > 0.dp)
                Modifier.height(maxHeight)
            else Modifier
        ),
        key = { it },
        pageSpacing = 8.dp,
    ) { index ->
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .onGloballyPositioned { coordinates ->
                    val heightDp = with(density) {
                        coordinates.size.height.toDp()
                    }
                    if (heightDp > maxHeight) {
                        maxHeight = heightDp
                    }
                }
        ) {
            state.pages[index]()
        }
    }
}
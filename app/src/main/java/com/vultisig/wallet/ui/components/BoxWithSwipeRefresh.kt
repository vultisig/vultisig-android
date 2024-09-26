package com.vultisig.wallet.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.pullToRefresh
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.rememberNestedScrollInteropConnection

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BoxWithSwipeRefresh(
    onSwipe: () -> Unit,
    isRefreshing: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val onRefresh: () -> Unit = {
        coroutineScope.launch {
            delay(5000)
            itemCount += 5
            isRefreshing = false
        }
    }


    val state = rememberPullToRefreshState()
    rememberNestedScrollInteropConnection()

    if (state.isAnimating) {
        LaunchedEffect(true) {
            onSwipe()
        }
    }

    if (!isRefreshing) {
        LaunchedEffect(true) {
            state.animateToHidden()
        }
    }

    Box(
        modifier = modifier.nestedScroll(rememberNestedScrollInteropConnection())
    ) {
        content()
        Modifier.pullToRefresh(
            state = state,
            isRefreshing = isRefreshing,
            onRefresh = onRefresh
        )
    }
}
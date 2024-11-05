package com.vultisig.wallet.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BoxWithSwipeRefresh(
    onSwipe: () -> Unit,
    isRefreshing: Boolean,
    modifier: Modifier = Modifier,
    hasPullToRefresh: Boolean = true,
    content: @Composable BoxScope.() -> Unit,
) {
    if (hasPullToRefresh) {
        PullToRefreshBox(
            modifier = modifier,
            onRefresh = onSwipe,
            content = content,
            isRefreshing = isRefreshing,
        )
    } else {
        Box(
            modifier = modifier,
            content = content,
        )
    }
}
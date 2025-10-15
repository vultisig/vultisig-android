package com.vultisig.wallet.ui.components.v2.scaffold

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import com.vultisig.wallet.ui.components.v2.snackbar.VSSnackbarState
import com.vultisig.wallet.ui.components.v2.snackbar.VsSnackBar
import com.vultisig.wallet.ui.components.v2.snackbar.rememberVsSnackbarState
import com.vultisig.wallet.ui.theme.Theme


@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ScaffoldWithExpandableTopBar(
    modifier: Modifier = Modifier,
    snackbarState: VSSnackbarState = rememberVsSnackbarState(),
    backgroundColor: Color = Theme.colors.backgrounds.primary,
    topBarExpandedContent: @Composable BoxScope.() -> Unit,
    topBarCollapsedContent: (@Composable BoxScope.() -> Unit)? = null,
    scrollBehavior: TopAppBarScrollBehavior =
        if (topBarCollapsedContent == null)
            TopAppBarDefaults.pinnedScrollBehavior()
        else
            TopAppBarDefaults.exitUntilCollapsedScrollBehavior(),
    bottomBarContent: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
    isRefreshing: Boolean = false,
    onRefresh: () -> Unit = {},
) {

    PullToRefreshBox(
        onRefresh = onRefresh,
        isRefreshing = isRefreshing,
    ) {
        Scaffold(
            modifier = modifier
                .nestedScroll(scrollBehavior.nestedScrollConnection),
            topBar = {
                if (topBarCollapsedContent != null) {
                    VsExpandableTopBar(
                        expandedContent = topBarExpandedContent,
                        collapsedContent = topBarCollapsedContent,
                        scrollBehavior = scrollBehavior,
                        backgroundColor = backgroundColor,
                    )
                } else {
                    Box(
                        content = topBarExpandedContent
                    )
                }
            },
            bottomBar = bottomBarContent,
            content = content
        )
        VsSnackBar(
            snackbarState = snackbarState,
            modifier = Modifier
                .align(alignment = Alignment.BottomCenter)
        )
    }
}



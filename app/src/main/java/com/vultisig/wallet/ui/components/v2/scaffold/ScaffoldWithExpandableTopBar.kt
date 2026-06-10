package com.vultisig.wallet.ui.components.v2.scaffold

import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.rememberScrollableState
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
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
    backgroundColor: Color = Theme.v2.colors.backgrounds.primary,
    topBarExpandedContent: @Composable BoxScope.() -> Unit,
    topBarCollapsedContent: (@Composable BoxScope.() -> Unit)? = null,
    scrollBehavior: TopAppBarScrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(),
    bottomBarContent: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
    isRefreshing: Boolean = false,
    onRefresh: () -> Unit = {},
) {
    // The bar collapses/re-grows entirely through the standard exitUntilCollapsed nested-scroll
    // connection. When the bar is collapsed and the user pulls down at the top, that connection
    // re-grows the bar with the leading over-scroll and, once it reaches its fully-expanded bound,
    // stops consuming — the remaining over-scroll then flows up to the enclosing PullToRefreshBox
    // and arms the refresh in the same gesture (#4752). Driving it through this single connection
    // (rather than a side animation on the shared height offset) keeps one writer to the offset, so
    // a non-monotonic pull can no longer make the bar oscillate.
    PullToRefreshBox(onRefresh = onRefresh, isRefreshing = isRefreshing) {
        Scaffold(
            modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
            containerColor = backgroundColor,
            topBar = {
                VsExpandableTopBar(
                    expandedContent = topBarExpandedContent,
                    collapsedContent = topBarCollapsedContent ?: {},
                    scrollBehavior = scrollBehavior,
                    backgroundColor = backgroundColor,
                )
            },
            bottomBar = bottomBarContent,
        ) { paddingValues ->
            // A downward drag that starts on a non-scrollable region of the content (the empty area
            // below a short list, headers, banners) would otherwise never reach PullToRefreshBox,
            // so
            // pull-to-refresh only worked when the drag began on the list itself (#4752). This
            // gesture-only `scrollable` (it consumes nothing and does NOT change layout, unlike
            // verticalScroll) forwards those drags into the nested-scroll system. Drags that land
            // on
            // the real list are handled by the list first; only the otherwise-dead regions fall
            // through to here.
            Box(
                modifier =
                    Modifier.fillMaxSize()
                        .scrollable(
                            orientation = Orientation.Vertical,
                            state = rememberScrollableState { 0f },
                            // No overscroll effect: this gesture-only scrollable must forward the
                            // list's over-scroll up to PullToRefreshBox, not absorb it as a
                            // stretch.
                            overscrollEffect = null,
                        )
            ) {
                content(paddingValues)
            }
        }
        VsSnackBar(
            snackbarState = snackbarState,
            modifier = Modifier.align(alignment = Alignment.BottomCenter),
        )
    }
}

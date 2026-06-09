package com.vultisig.wallet.ui.components.v2.scaffold

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.Velocity
import com.vultisig.wallet.ui.components.v2.snackbar.VSSnackbarState
import com.vultisig.wallet.ui.components.v2.snackbar.VsSnackBar
import com.vultisig.wallet.ui.components.v2.snackbar.rememberVsSnackbarState
import com.vultisig.wallet.ui.theme.Theme
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

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
    val expandScope = rememberCoroutineScope()
    val expandJob = remember { mutableStateOf<Job?>(null) }

    // A downward over-scroll at the top must reach the enclosing PullToRefreshBox to arm a refresh.
    // The default exitUntilCollapsed connection instead consumes that over-scroll frame-by-frame to
    // re-grow the collapsed top bar, which starved pull-to-refresh — so a pull made while the bar
    // was collapsed never triggered a refresh (#4752). This wrapper keeps collapse-on-scroll-up,
    // but
    // when the user over-scrolls down at the top with the bar collapsed it animates the bar open
    // once
    // and lets the over-scroll pass through, so the bar reappears and the refresh arms in the same
    // gesture.
    val pullRefreshAwareConnection =
        remember(scrollBehavior, expandScope) {
            object : NestedScrollConnection {
                override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                    if (available.y < 0f) {
                        expandJob.value?.cancel()
                        expandJob.value = null
                    }
                    return scrollBehavior.nestedScrollConnection.onPreScroll(available, source)
                }

                override fun onPostScroll(
                    consumed: Offset,
                    available: Offset,
                    source: NestedScrollSource,
                ): Offset {
                    val barCollapsed = scrollBehavior.state.heightOffset < 0f
                    if (
                        available.y > 0f && source == NestedScrollSource.UserInput && barCollapsed
                    ) {
                        if (expandJob.value?.isActive != true) {
                            expandJob.value =
                                expandScope.launch {
                                    animate(
                                        initialValue = scrollBehavior.state.heightOffset,
                                        targetValue = 0f,
                                        animationSpec =
                                            tween(
                                                durationMillis = 250,
                                                easing = FastOutSlowInEasing,
                                            ),
                                    ) { value, _ ->
                                        scrollBehavior.state.heightOffset = value
                                    }
                                }
                        }
                        // Don't consume: hand the over-scroll to PullToRefreshBox so it can arm.
                        return Offset.Zero
                    }
                    return scrollBehavior.nestedScrollConnection.onPostScroll(
                        consumed,
                        available,
                        source,
                    )
                }

                override suspend fun onPreFling(available: Velocity): Velocity =
                    scrollBehavior.nestedScrollConnection.onPreFling(available)

                override suspend fun onPostFling(
                    consumed: Velocity,
                    available: Velocity,
                ): Velocity = scrollBehavior.nestedScrollConnection.onPostFling(consumed, available)
            }
        }

    PullToRefreshBox(onRefresh = onRefresh, isRefreshing = isRefreshing) {
        Scaffold(
            modifier = modifier.nestedScroll(pullRefreshAwareConnection),
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

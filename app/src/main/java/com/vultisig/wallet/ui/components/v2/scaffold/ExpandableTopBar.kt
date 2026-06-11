@file:OptIn(ExperimentalMaterial3Api::class)

package com.vultisig.wallet.ui.components.v2.scaffold

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Surface
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollDispatcher
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp

@Composable
fun VsExpandableTopBar(
    scrollBehavior: TopAppBarScrollBehavior,
    modifier: Modifier = Modifier,
    backgroundColor: Color,
    expandedContent: @Composable BoxScope.() -> Unit,
    collapsedContent: @Composable BoxScope.() -> Unit,
) {
    var expandedHeightPx by remember { mutableIntStateOf(0) }
    var collapsedHeightPx by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    var isMeasuring by remember { mutableStateOf(true) }

    if (isMeasuring) {
        Box(
            modifier =
                Modifier.alpha(0f).fillMaxWidth().onGloballyPositioned { coordinates ->
                    val newHeight = coordinates.size.height
                    if (newHeight > 0) {
                        expandedHeightPx = newHeight
                    }
                },
            content = expandedContent,
        )

        Box(
            modifier =
                Modifier.alpha(0f).fillMaxWidth().onGloballyPositioned { coordinates ->
                    collapsedHeightPx = coordinates.size.height
                    if (expandedHeightPx > 0) {
                        isMeasuring = false
                    }
                },
            content = collapsedContent,
        )
    }

    Layout(
        modifier = Modifier.alpha(0f),
        content = {
            Box(modifier = Modifier.fillMaxWidth(), content = expandedContent)
            Box(modifier = Modifier.fillMaxWidth(), content = collapsedContent)
        },
    ) { measurables, constraints ->
        val expandedPlaceable = measurables[0].measure(constraints)
        val collapsedPlaceable = measurables[1].measure(constraints)

        if (expandedPlaceable.height > 0 && expandedPlaceable.height != expandedHeightPx) {
            expandedHeightPx = expandedPlaceable.height
        }
        if (collapsedPlaceable.height != collapsedHeightPx) {
            collapsedHeightPx = collapsedPlaceable.height
        }

        layout(0, 0) {}
    }

    if (!isMeasuring && expandedHeightPx > 0) {
        val heightDiffPx =
            remember(expandedHeightPx, collapsedHeightPx) {
                (expandedHeightPx - collapsedHeightPx).toFloat()
            }
        val offsetLimit = remember(heightDiffPx) { -heightDiffPx }

        // Single source of truth for the bar height: scrollBehavior.state.heightOffset. Both the
        // direct drag below and the nested-scroll from the list write this same value, so they can
        // never diverge (no separate Animatable that goes stale when the list scrolls the bar).
        val previousOffsetLimit = remember { mutableFloatStateOf(offsetLimit) }

        LaunchedEffect(offsetLimit) {
            val oldLimit = previousOffsetLimit.floatValue
            val currentOffset = scrollBehavior.state.heightOffset

            // Update the limit first so the coercing setter below accepts the rescaled offset.
            scrollBehavior.state.heightOffsetLimit = offsetLimit

            if (oldLimit != 0f && oldLimit != offsetLimit && currentOffset != 0f) {
                val collapsePercentage = (currentOffset / oldLimit).coerceIn(0f, 1f)
                val newOffset = (offsetLimit * collapsePercentage).coerceIn(offsetLimit, 0f)
                scrollBehavior.state.heightOffset = newOffset
            } else {
                scrollBehavior.state.heightOffset = currentOffset.coerceIn(offsetLimit, 0f)
            }

            previousOffsetLimit.floatValue = offsetLimit
        }

        val offset by remember { derivedStateOf { scrollBehavior.state.heightOffset } }

        val collapseFraction =
            remember(offset, offsetLimit) {
                if (offsetLimit < 0f) {
                    (offset / offsetLimit).coerceIn(0f, 1f)
                } else {
                    0f
                }
            }

        val expandedFraction = remember(collapseFraction) { 1f - collapseFraction }
        val currentHeightPx =
            remember(expandedFraction, expandedHeightPx, collapsedHeightPx) {
                collapsedHeightPx + (expandedHeightPx - collapsedHeightPx) * expandedFraction
            }

        // Bridges the bar's own drag gesture into the nested-scroll system. When the bar is at a
        // bound (fully expanded and over-dragged downward), the unused delta is dispatched to the
        // surrounding parents so a downward pull that starts on the top bar reaches the enclosing
        // PullToRefreshBox instead of being swallowed by this draggable (#4752).
        val nestedScrollDispatcher = remember { NestedScrollDispatcher() }
        val noOpConnection = remember { object : NestedScrollConnection {} }

        val dragState = rememberDraggableState { delta ->
            val current = scrollBehavior.state.heightOffset
            val target = (current + delta).coerceIn(offsetLimit, 0f)
            scrollBehavior.state.heightOffset = target
            val leftover = delta - (target - current)
            if (leftover != 0f) {
                nestedScrollDispatcher.dispatchPostScroll(
                    consumed = Offset(0f, target - current),
                    available = Offset(0f, leftover),
                    source = NestedScrollSource.UserInput,
                )
            }
        }

        Surface(
            modifier =
                modifier
                    .height(with(density) { currentHeightPx.toDp() })
                    .nestedScroll(connection = noOpConnection, dispatcher = nestedScrollDispatcher)
                    .draggable(
                        orientation = Orientation.Vertical,
                        state = dragState,
                        onDragStopped = { velocity ->
                            if (scrollBehavior.state.heightOffset >= 0f) {
                                // The bar is fully expanded and the user kept over-dragging
                                // downward: hand the release velocity to the nested-scroll parents
                                // so PullToRefreshBox can settle / trigger. The bar itself does not
                                // move, so there is nothing for us to settle.
                                val available = Velocity(0f, velocity)
                                val consumedVelocity =
                                    nestedScrollDispatcher.dispatchPreFling(available)
                                nestedScrollDispatcher.dispatchPostFling(
                                    consumedVelocity,
                                    available - consumedVelocity,
                                )
                            } else {
                                // The bar is partially dragged: settle it to the nearest state.
                                // We do NOT dispatch the fling to the parent here — doing so would
                                // let exitUntilCollapsed.onPostFling settle the bar too, producing
                                // a settle-then-jump-back double animation on the shared offset.
                                val target = if (expandedFraction < 0.5f) offsetLimit else 0f
                                animate(
                                    initialValue = scrollBehavior.state.heightOffset,
                                    targetValue = target,
                                    animationSpec =
                                        tween(durationMillis = 300, easing = FastOutSlowInEasing),
                                ) { value, _ ->
                                    scrollBehavior.state.heightOffset = value
                                }
                            }
                        },
                    ),
            tonalElevation = 0.dp,
            color = backgroundColor,
        ) {
            Crossfade(
                targetState = expandedFraction > 0.5f,
                animationSpec = tween(durationMillis = 150),
                label = "content_transition",
            ) { isExpanded ->
                Box(
                    modifier = Modifier.fillMaxSize(),
                    content = if (isExpanded) expandedContent else collapsedContent,
                )
            }
        }
    }
}

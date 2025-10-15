@file:OptIn(ExperimentalMaterial3Api::class)

package com.vultisig.wallet.ui.components.v2.scaffold

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@Composable
fun VsExpandableTopBar(
    scrollBehavior: TopAppBarScrollBehavior,
    modifier: Modifier = Modifier,
    backgroundColor: Color,
    expandedContent: @Composable BoxScope.() -> Unit,
    collapsedContent: @Composable BoxScope.() -> Unit
) {
    var expandedHeightPx by remember { mutableIntStateOf(0) }
    var collapsedHeightPx by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current


    if (expandedHeightPx > 0 && collapsedHeightPx > 0) {
        val heightDiffPx = remember(expandedHeightPx, collapsedHeightPx) {
            (expandedHeightPx - collapsedHeightPx).toFloat()
        }
        val offsetLimit = remember(heightDiffPx) { -heightDiffPx }


        LaunchedEffect(offsetLimit) {
            scrollBehavior.state.heightOffsetLimit = offsetLimit
        }


        val offset by remember {
            derivedStateOf { scrollBehavior.state.heightOffset }
        }

        val collapseFraction = remember(offset, heightDiffPx) {
            if (heightDiffPx > 0f) {
                (offset / offsetLimit).coerceIn(0f, 1f)
            } else {
                0f
            }
        }

        val expandedFraction = remember(collapseFraction) { 1f - collapseFraction }
        val currentHeightPx = remember(expandedFraction, expandedHeightPx, collapsedHeightPx) {
            collapsedHeightPx + (expandedHeightPx - collapsedHeightPx) * expandedFraction
        }

        val animatedOffset = remember { Animatable(0f) }

        LaunchedEffect(Unit) {
            snapshotFlow { animatedOffset.value }
                .collect { value ->
                    scrollBehavior.state.heightOffset = value
                }
        }

        val coroutineScope = rememberCoroutineScope()

        val dragState = rememberDraggableState { delta ->
            coroutineScope.launch {
                animatedOffset.updateBounds(lowerBound = offsetLimit, upperBound = 0f)
                animatedOffset.snapTo((animatedOffset.value + delta).coerceIn(offsetLimit, 0f))
            }
        }

        Surface(
            modifier = modifier
                .height(with(density) { currentHeightPx.toDp() })
                .draggable(
                    orientation = Orientation.Vertical,
                    state = dragState,
                    onDragStopped = {
                        val target = if (expandedFraction < 0.5f) offsetLimit else 0f
                        animatedOffset.animateTo(
                            targetValue = target,
                            animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing)
                        )
                    }
                ),
            tonalElevation = 0.dp,
            color = backgroundColor
        ) {

            Crossfade(
                targetState = expandedFraction > 0.5f,
                animationSpec = tween(durationMillis = 150),
                label = "content_transition"
            ) { isExpanded ->
                Box(
                    modifier = Modifier.fillMaxSize(),
                    content = if (isExpanded) expandedContent else collapsedContent
                )
            }
        }
    } else {

        MeasurementPhase(
            modifier = modifier,
            expandedContent = expandedContent,
            collapsedContent = collapsedContent,
            onExpandedHeightMeasured = { expandedHeightPx = it },
            onCollapsedHeightMeasured = { collapsedHeightPx = it }
        )
    }
}

@Composable
private fun MeasurementPhase(
    modifier: Modifier,
    expandedContent: @Composable BoxScope.() -> Unit,
    collapsedContent: @Composable BoxScope.() -> Unit,
    onExpandedHeightMeasured: (Int) -> Unit,
    onCollapsedHeightMeasured: (Int) -> Unit
) {
    val hasMeasuredExpanded = remember { mutableStateOf(false) }
    val hasMeasuredCollapsed = remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        Box(
            modifier = Modifier
                .alpha(0f)
                .fillMaxWidth()
                .onGloballyPositioned { coordinates ->
                    if (!hasMeasuredExpanded.value && coordinates.size.height > 0) {
                        onExpandedHeightMeasured(coordinates.size.height)
                        hasMeasuredExpanded.value = true
                    }
                },
            content = expandedContent
        )

        Box(
            modifier = Modifier
                .alpha(0f)
                .fillMaxWidth()
                .onGloballyPositioned { coordinates ->
                    if (!hasMeasuredCollapsed.value && coordinates.size.height > 0) {
                        onCollapsedHeightMeasured(coordinates.size.height)
                        hasMeasuredCollapsed.value = true
                    }
                },
            content = collapsedContent
        )

        Box(
            modifier = Modifier.fillMaxWidth(),
            content = expandedContent
        )
    }
}
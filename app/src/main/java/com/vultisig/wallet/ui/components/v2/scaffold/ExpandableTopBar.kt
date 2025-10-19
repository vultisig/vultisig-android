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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
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
    var isMeasuring by remember { mutableStateOf(true) }

    if (isMeasuring) {
        Box(
            modifier = Modifier
                .alpha(0f)
                .fillMaxWidth()
                .onGloballyPositioned { coordinates ->
                    val newHeight = coordinates.size.height
                    if (newHeight > 0) {
                        expandedHeightPx = newHeight
                    }
                },
            content = expandedContent
        )

        Box(
            modifier = Modifier
                .alpha(0f)
                .fillMaxWidth()
                .onGloballyPositioned { coordinates ->
                    val newHeight = coordinates.size.height
                    if (newHeight > 0) {
                        collapsedHeightPx = newHeight
                        if (expandedHeightPx > 0) {
                            isMeasuring = false
                        }
                    }
                },
            content = collapsedContent
        )
    }


    Layout(
        modifier = Modifier.alpha(0f),
        content = {
            Box(
                modifier = Modifier.fillMaxWidth(),
                content = expandedContent
            )
            Box(
                modifier = Modifier.fillMaxWidth(),
                content = collapsedContent
            )
        }
    ) { measurables, constraints ->
        val expandedPlaceable = measurables[0].measure(constraints)
        val collapsedPlaceable = measurables[1].measure(constraints)

        if (expandedPlaceable.height > 0 && expandedPlaceable.height != expandedHeightPx) {
            expandedHeightPx = expandedPlaceable.height
        }
        if (collapsedPlaceable.height > 0 && collapsedPlaceable.height != collapsedHeightPx) {
            collapsedHeightPx = collapsedPlaceable.height
        }

        layout(0, 0) {}
    }

    if (!isMeasuring && expandedHeightPx > 0 && collapsedHeightPx > 0) {
        val heightDiffPx = remember(expandedHeightPx, collapsedHeightPx) {
            (expandedHeightPx - collapsedHeightPx).toFloat()
        }
        val offsetLimit = remember(heightDiffPx) { -heightDiffPx }

        val animatedOffset = remember { Animatable(0f) }

        val previousOffsetLimit = remember { mutableFloatStateOf(offsetLimit) }

        LaunchedEffect(offsetLimit) {
            val oldLimit = previousOffsetLimit.floatValue
            val currentOffset = animatedOffset.value

            animatedOffset.updateBounds(lowerBound = offsetLimit, upperBound = 0f)

            if (oldLimit != 0f && oldLimit != offsetLimit && currentOffset != 0f) {
                val collapsePercentage = (currentOffset / oldLimit).coerceIn(0f, 1f)
                val newOffset = (offsetLimit * collapsePercentage).coerceIn(offsetLimit, 0f)
                animatedOffset.snapTo(newOffset)
            } else {
                val clampedValue = currentOffset.coerceIn(offsetLimit, 0f)
                if (clampedValue != currentOffset) {
                    animatedOffset.snapTo(clampedValue)
                }
            }

            scrollBehavior.state.heightOffsetLimit = offsetLimit
            previousOffsetLimit.floatValue = offsetLimit
        }

        LaunchedEffect(Unit) {
            snapshotFlow { animatedOffset.value }
                .collect { value ->
                    scrollBehavior.state.heightOffset = value
                }
        }

        val offset by remember {
            derivedStateOf { scrollBehavior.state.heightOffset }
        }

        val collapseFraction = remember(offset, offsetLimit) {
            if (offsetLimit < 0f) {
                (offset / offsetLimit).coerceIn(0f, 1f)
            } else {
                0f
            }
        }

        val expandedFraction = remember(collapseFraction) { 1f - collapseFraction }
        val currentHeightPx = remember(expandedFraction, expandedHeightPx, collapsedHeightPx) {
            collapsedHeightPx + (expandedHeightPx - collapsedHeightPx) * expandedFraction
        }

        val coroutineScope = rememberCoroutineScope()

        val dragState = rememberDraggableState { delta ->
            coroutineScope.launch {
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
    }
}
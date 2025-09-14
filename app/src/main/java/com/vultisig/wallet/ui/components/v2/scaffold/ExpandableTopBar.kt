@file:OptIn(ExperimentalMaterial3Api::class)

package com.vultisig.wallet.ui.components.v2.scaffold

import androidx.compose.animation.core.Animatable
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
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import kotlinx.coroutines.launch


@Composable
fun VsExpandableTopBar(
    scrollBehavior: TopAppBarScrollBehavior,
    modifier: Modifier = Modifier,
    backgroundColor : Color,
    expandedContent: @Composable BoxScope.() -> Unit,
    collapsedContent: @Composable BoxScope.() -> Unit
) {
    var expandedHeight by remember { mutableStateOf(0.dp) }
    var collapsedHeight by remember { mutableStateOf(0.dp) }
    val density = LocalDensity.current

    if (expandedHeight > 0.dp && collapsedHeight > 0.dp) {
        val heightPx = with(density) { (expandedHeight - collapsedHeight).toPx() }
        val offset = scrollBehavior.state.heightOffset
        val offsetLimit = -heightPx

        val collapseFraction = if (heightPx > 0) {
            (offset / offsetLimit).coerceIn(0f, 1f)
        } else {
            0f
        }

        val expandedFraction = 1f - collapseFraction
        val height = lerp(collapsedHeight, expandedHeight, expandedFraction)

        val coroutineScope = rememberCoroutineScope()

        val animatedOffset = remember { Animatable(0f) }

        LaunchedEffect(animatedOffset.value) {
            scrollBehavior.state.heightOffset = animatedOffset.value
        }


        SideEffect {
            if (scrollBehavior.state.heightOffsetLimit != -heightPx) {
                scrollBehavior.state.heightOffsetLimit = -heightPx
            }
        }

        Surface(
            modifier = modifier
                .height(height)
                .draggable(
                    orientation = Orientation.Vertical,
                    state = rememberDraggableState { delta ->
                        coroutineScope.launch {
                            animatedOffset.snapTo(
                                (animatedOffset.value + delta)
                                    .coerceIn(-heightPx, 0f)
                            )
                        }
                    },
                    onDragStopped = {
                        coroutineScope.launch {
                            val shouldCollapse = expandedFraction < 0.5f
                            val target = if (shouldCollapse) -heightPx else 0f
                            animatedOffset.animateTo(target, tween(300))
                        }
                    }
                ),

            tonalElevation = 0.dp,
            color = backgroundColor
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                content = if (expandedFraction > 0.5f) {
                    expandedContent
                } else {
                    collapsedContent
                }
            )
        }
    } else {
        Box(modifier = modifier) {
            // Measure expanded content (invisible)
            Box(
                modifier = Modifier
                    .alpha(0f)
                    .fillMaxWidth()
                    .onGloballyPositioned { coordinates ->
                        expandedHeight = with(density) { coordinates.size.height.toDp() }
                    },
                content = expandedContent
            )
            // Measure collapsed content (invisible)
            Box(
                modifier = Modifier
                    .alpha(0f)
                    .fillMaxWidth()
                    .onGloballyPositioned { coordinates ->
                        collapsedHeight = with(density) { coordinates.size.height.toDp() }
                    },
                content = collapsedContent
            )

            // Show expanded content by default during measurement
            Surface(
                tonalElevation = 0.dp
            ) {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    content = expandedContent
                )
            }
        }
    }
}
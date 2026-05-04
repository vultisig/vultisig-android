package com.vultisig.wallet.ui.components.v2.fastselection.components

import android.annotation.SuppressLint
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.vultisig.wallet.ui.screens.send.FadingHorizontalDivider
import com.vultisig.wallet.ui.theme.Theme
import kotlin.math.abs
import kotlinx.coroutines.launch

internal val FAST_SELECTION_MODAL_WIDTH = 300.dp

/**
 * Returns a namespaced key for one entry in the padded list.
 *
 * Real items use the `"item"` namespace so their keys can never collide with padding entries that
 * use the `"padding"` namespace, even when [key] happens to return a value that looks like a
 * position index.
 */
internal fun <T : Any> paddedItemKey(index: Int, item: T?, key: (T) -> Any): Any =
    if (item != null) Pair("item", key(item)) else Pair("padding", index)

/**
 * Builds the complete key list for a padded item list.
 *
 * Adds [visibleItemCount]/2 null-padding slots at both ends of [items] and returns a key for every
 * slot via [paddedItemKey]. All returned keys are guaranteed to be unique as long as [key] returns
 * a distinct value for each element of [items].
 */
internal fun <T : Any> buildPaddedItemKeys(
    items: List<T>,
    visibleItemCount: Int,
    key: (T) -> Any,
): List<Any> {
    val paddingCount = visibleItemCount / 2
    val paddedItems: List<T?> = buildList {
        repeat(paddingCount) { add(null) }
        addAll(items)
        repeat(paddingCount) { add(null) }
    }
    return paddedItems.mapIndexed { index, item -> paddedItemKey(index, item, key) }
}

/**
 * Scrollable picker list that positions itself near the user's drag gesture.
 *
 * @param modifier Applied to the outermost [Box].
 * @param items Selectable items; must not contain nulls (nulls are used internally for padding).
 * @param currentIndex Index into [items] that is currently selected.
 * @param pressPosition Absolute screen coordinates of the pointer, used to place the popup.
 * @param visibleItemCount How many rows to display at once; an odd value centres the selection.
 * @param key Stable, unique identity for each item — passed as the [LazyColumn] item key to prevent
 *   [IllegalArgumentException] from key collisions during [AnimatedVisibility] transitions when the
 *   underlying list changes concurrently.
 * @param onItemHeightMeasured Called once with the measured row height in pixels.
 * @param itemContent Composable slot rendered for each non-null item.
 */
@SuppressLint("ConfigurationScreenWidthHeight")
@Composable
internal fun <T : Any> FastSelectionModalContent(
    modifier: Modifier,
    items: List<T>,
    currentIndex: Int,
    pressPosition: Offset,
    visibleItemCount: Int,
    key: (T) -> Any,
    onItemHeightMeasured: (Int) -> Unit,
    itemContent: @Composable (item: T, distanceFromCenter: Int) -> Unit,
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current

    var itemHeightPx by remember { mutableIntStateOf(0) }
    var isHeightMeasured by remember { mutableStateOf(false) }

    val modalWidth = with(density) { (configuration.screenWidthDp * 0.85f).dp.toPx() }

    val modalHeight = itemHeightPx * visibleItemCount
    val centerOffset = (modalHeight / 2 - itemHeightPx / 2)

    val maximumXOffset =
        (configuration.screenWidthDp * density.density - modalWidth).coerceAtLeast(0f)
    val xOffset =
        if (modalWidth > 0) {
            (pressPosition.x - modalWidth / 2).coerceIn(0f, maximumXOffset)
        } else 0f

    val maximumYOffset = configuration.screenHeightDp * density.density - modalHeight
    val yOffset =
        if (modalHeight > 0) {
            (pressPosition.y - modalHeight / 2).coerceIn(0f, maximumYOffset)
        } else 0f

    LaunchedEffect(currentIndex, isHeightMeasured) {
        if (currentIndex in items.indices && isHeightMeasured) {
            scope.launch {
                val paddingItems = visibleItemCount / 2
                listState.animateScrollToItem(
                    index = currentIndex + paddingItems,
                    scrollOffset = -centerOffset,
                )
            }
        }
    }

    LaunchedEffect(isHeightMeasured) {
        if (isHeightMeasured) {
            val paddingItems = visibleItemCount / 2
            listState.scrollToItem(
                index = currentIndex + paddingItems,
                scrollOffset = -centerOffset,
            )
        }
    }

    Box(modifier = modifier) {
        AnimatedVisibility(
            visible = true,
            enter =
                scaleIn(
                    initialScale = 0.8f,
                    transformOrigin = TransformOrigin.Center,
                    animationSpec =
                        spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessMedium,
                        ),
                ) + fadeIn(animationSpec = tween(durationMillis = 200)),
            exit =
                scaleOut(targetScale = 0.8f, animationSpec = tween(durationMillis = 150)) +
                    fadeOut(animationSpec = tween(durationMillis = 150)),
        ) {
            Box(
                modifier =
                    Modifier.then(
                            if (isHeightMeasured) {
                                Modifier.height(with(density) { modalHeight.toDp() })
                            } else {
                                Modifier.wrapContentHeight()
                            }
                        )
                        .clip(RoundedCornerShape(24.dp))
                        .background(
                            color = Theme.v2.colors.backgrounds.tertiary_2.copy(alpha = 0.5f)
                        )
            ) {
                val paddingItems = visibleItemCount / 2
                val paddedItems = buildList {
                    repeat(paddingItems) { add(null) }
                    addAll(items)
                    repeat(paddingItems) { add(null) }
                }

                LazyColumn(
                    state = listState,
                    modifier =
                        Modifier.fillMaxHeight()
                            .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
                            .drawWithContent {
                                drawContent()
                                drawRect(
                                    brush =
                                        Brush.verticalGradient(
                                            0f to Color.Transparent,
                                            0.5f to Color.Black,
                                            1f to Color.Transparent,
                                        ),
                                    blendMode = BlendMode.DstIn,
                                )
                            },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    userScrollEnabled = false,
                ) {
                    itemsIndexed(
                        paddedItems,
                        key = { index, item -> paddedItemKey(index, item, key) },
                    ) { index, item ->
                        val actualIndex = index - paddingItems

                        if (item != null) {
                            val distanceFromCenter = abs(actualIndex - currentIndex)

                            Box(
                                modifier =
                                    Modifier.onGloballyPositioned { coordinates ->
                                        if (!isHeightMeasured && coordinates.size.height > 0) {
                                            itemHeightPx = coordinates.size.height
                                            onItemHeightMeasured(coordinates.size.height)
                                            isHeightMeasured = true
                                        }
                                    },
                                contentAlignment = Alignment.Center,
                            ) {
                                itemContent(item, distanceFromCenter)
                            }
                        } else {
                            if (isHeightMeasured) {
                                Spacer(
                                    modifier =
                                        Modifier.height(with(density) { itemHeightPx.toDp() })
                                )
                            } else {
                                Spacer(modifier = Modifier.height(16.dp))
                            }
                        }
                    }
                }

                val itemHeight = with(density) { itemHeightPx.toDp() }

                FadingHorizontalDivider(
                    modifier =
                        Modifier.width(FAST_SELECTION_MODAL_WIDTH)
                            .align(Alignment.Center)
                            .offset(y = -itemHeight / 2)
                )
                FadingHorizontalDivider(
                    modifier =
                        Modifier.width(FAST_SELECTION_MODAL_WIDTH)
                            .align(Alignment.Center)
                            .offset(y = itemHeight / 2)
                )
            }
        }
    }
}

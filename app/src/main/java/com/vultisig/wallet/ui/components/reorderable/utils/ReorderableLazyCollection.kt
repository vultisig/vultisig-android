package com.vultisig.wallet.ui.components.reorderable.utils

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toOffset
import androidx.compose.ui.unit.toSize
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout

object ReorderableLazyCollectionDefaults {
    val ScrollThreshold: Dp = 48.dp
}

internal const val ScrollAmountMultiplier = 0.05f

internal data class AbsolutePixelPadding(
    val start: Float,
    val end: Float,
    val top: Float,
    val bottom: Float,
)

internal interface LazyCollectionItemInfo<out T> {
    val index: Int
    val key: Any
    val offset: IntOffset
    val size: IntSize
    val data: T

    val center: IntOffset
        get() = IntOffset(offset.x + size.width / 2, offset.y + size.height / 2)
}

internal data class CollectionScrollPadding(
    val start: Float,
    val end: Float,
) {
    companion object {
        val Zero = CollectionScrollPadding(0f, 0f)

        fun fromAbsolutePixelPadding(
            orientation: Orientation,
            padding: AbsolutePixelPadding,
            reverseLayout: Boolean,
        ): CollectionScrollPadding {
            return when (orientation) {
                Orientation.Vertical -> CollectionScrollPadding(
                    start = padding.top,
                    end = padding.bottom,
                )

                Orientation.Horizontal -> CollectionScrollPadding(
                    start = padding.start,
                    end = padding.end,
                )
            }.let {
                when (reverseLayout) {
                    true -> CollectionScrollPadding(
                        start = it.end,
                        end = it.start,
                    )

                    false -> it
                }
            }
        }
    }
}

internal data class ScrollAreaOffsets(
    val start: Float,
    val end: Float,
)

internal interface LazyCollectionLayoutInfo<out T> {
    val visibleItemsInfo: List<LazyCollectionItemInfo<T>>
    val viewportSize: IntSize
    val orientation: Orientation
    val reverseLayout: Boolean
    val beforeContentPadding: Int

    val mainAxisViewportSize: Int
        get() = when (orientation) {
            Orientation.Vertical -> viewportSize.height
            Orientation.Horizontal -> viewportSize.width
        }

    fun getScrollAreaOffsets(
        padding: AbsolutePixelPadding,
    ) = getScrollAreaOffsets(
        CollectionScrollPadding.fromAbsolutePixelPadding(
            orientation,
            padding,
            reverseLayout,
        )
    )

    fun getScrollAreaOffsets(padding: CollectionScrollPadding): ScrollAreaOffsets {
        val (startPadding, endPadding) = padding
        val contentEndOffset = when (orientation) {
            Orientation.Vertical -> viewportSize.height
            Orientation.Horizontal -> viewportSize.width
        } - endPadding

        return ScrollAreaOffsets(
            start = startPadding,
            end = contentEndOffset,
        )
    }


    fun getItemsInContentArea(padding: AbsolutePixelPadding) = getItemsInContentArea(
        CollectionScrollPadding.fromAbsolutePixelPadding(
            orientation,
            padding,
            reverseLayout,
        )
    )

    fun getItemsInContentArea(padding: CollectionScrollPadding = CollectionScrollPadding.Zero): List<LazyCollectionItemInfo<T>> {
        val (contentStartOffset, contentEndOffset) = getScrollAreaOffsets(
            padding
        )

        return when (orientation) {
            Orientation.Vertical -> {
                visibleItemsInfo.filter { item ->
                    item.offset.y >= contentStartOffset && item.offset.y + item.size.height <= contentEndOffset
                }
            }

            Orientation.Horizontal -> {
                visibleItemsInfo.filter { item ->
                    item.offset.x >= contentStartOffset && item.offset.x + item.size.width <= contentEndOffset
                }
            }
        }
    }
}

internal interface LazyCollectionState<out T> {
    val firstVisibleItemIndex: Int
    val firstVisibleItemScrollOffset: Int
    val layoutInfo: LazyCollectionLayoutInfo<T>

    suspend fun animateScrollBy(
        value: Float,
        animationSpec: AnimationSpec<Float> = spring(),
    ): Float

    suspend fun scrollToItem(scrollToIndex: Int, firstVisibleItemScrollOffset: Int)
}

interface ReorderableLazyCollectionStateInterface {
    val isAnyItemDragging: Boolean
}

@Stable
open class ReorderableLazyCollectionState<out T> internal constructor(
    private val state: LazyCollectionState<T>,
    private val scope: CoroutineScope,
    private val onMoveState: State<suspend CoroutineScope.(from: T, to: T) -> Unit>,


    private val scrollThreshold: Float,
    private val scrollThresholdPadding: AbsolutePixelPadding,
    private val scroller: Scroller,

    private val layoutDirection: LayoutDirection,

    private val lazyVerticalStaggeredGridRtlFix: Boolean = false,

    private val shouldItemMove: (draggingItem: Rect, item: Rect) -> Boolean = { draggingItem, item ->
        draggingItem.contains(item.center)
    },
) : ReorderableLazyCollectionStateInterface {
    private val onMoveStateMutex: Mutex = Mutex()

    internal val orientation: Orientation
        get() = state.layoutInfo.orientation

    private var draggingItemKey by mutableStateOf<Any?>(null)
    private val draggingItemIndex: Int?
        get() = draggingItemLayoutInfo?.index


    override val isAnyItemDragging: Boolean by derivedStateOf {
        draggingItemKey != null
    }

    private var draggingItemDraggedDelta by mutableStateOf(Offset.Zero)
    private var draggingItemInitialOffset by mutableStateOf(IntOffset.Zero)

    private var draggingItemTargetIndex by mutableStateOf<Int?>(null)
    private var predictedDraggingItemOffset by mutableStateOf<IntOffset?>(null)

    private val draggingItemLayoutInfo: LazyCollectionItemInfo<T>?
        get() = draggingItemKey?.let { draggingItemKey ->
            state.layoutInfo.visibleItemsInfo.firstOrNull { it.key == draggingItemKey }
        }
    internal val draggingItemOffset: Offset
        get() = (draggingItemLayoutInfo?.let {
            val offset =
                if (it.index == draggingItemTargetIndex || draggingItemTargetIndex == null) {
                    draggingItemTargetIndex = null
                    predictedDraggingItemOffset = null
                    it.offset
                } else {
                    predictedDraggingItemOffset ?: it.offset
                }

            draggingItemDraggedDelta +
                    (draggingItemInitialOffset.toOffset() - offset.toOffset())
                        .reverseAxisIfNecessary()
                        .reverseAxisWithLayoutDirectionIfLazyVerticalStaggeredGridRtlFix()
        }) ?: Offset.Zero

    private var draggingItemHandleOffset = Offset.Zero

    internal val reorderableKeys = HashSet<Any?>()

    internal var previousDraggingItemKey by mutableStateOf<Any?>(null)
        private set
    internal var previousDraggingItemOffset = Animatable(Offset.Zero, Offset.VectorConverter)
        private set

    private fun Offset.reverseAxisWithReverseLayoutIfNecessary() =
        when (state.layoutInfo.reverseLayout) {
            true -> reverseAxis(orientation)
            false -> this
        }

    private fun Offset.reverseAxisWithLayoutDirectionIfNecessary() = when (orientation) {
        Orientation.Vertical -> this
        Orientation.Horizontal -> reverseAxisWithLayoutDirection()
    }

    private fun Offset.reverseAxisWithLayoutDirection() = when (layoutDirection) {
        LayoutDirection.Ltr -> this
        LayoutDirection.Rtl -> reverseAxis(Orientation.Horizontal)
    }

    private fun Offset.reverseAxisWithLayoutDirectionIfLazyVerticalStaggeredGridRtlFix() =
        when (layoutDirection) {
            LayoutDirection.Ltr -> this
            LayoutDirection.Rtl -> if (lazyVerticalStaggeredGridRtlFix && orientation == Orientation.Vertical)
                reverseAxis(Orientation.Horizontal)
            else this
        }

    private fun Offset.reverseAxisIfNecessary() =
        this.reverseAxisWithReverseLayoutIfNecessary()
            .reverseAxisWithLayoutDirectionIfNecessary()

    private fun Offset.mainAxis() = getAxis(orientation)

    private fun IntOffset.mainAxis() = getAxis(orientation)

    internal suspend fun onDragStart(key: Any, handleOffset: Offset) {
        state.layoutInfo.visibleItemsInfo.firstOrNull { item ->
            item.key == key
        }?.also {
            val mainAxisOffset = it.offset.mainAxis()
            if (mainAxisOffset < 0) {
                state.animateScrollBy(mainAxisOffset.toFloat(), spring())
            }

            draggingItemKey = key
            draggingItemInitialOffset = it.offset
            draggingItemHandleOffset = handleOffset
        }
    }

    internal fun onDragStop() {
        val previousDraggingItemInitialOffset = draggingItemLayoutInfo?.offset

        if (draggingItemIndex != null) {
            previousDraggingItemKey = draggingItemKey
            val startOffset = draggingItemOffset
            scope.launch {
                previousDraggingItemOffset.snapTo(startOffset)
                previousDraggingItemOffset.animateTo(
                    Offset.Zero,
                    spring(
                        stiffness = Spring.StiffnessMediumLow,
                        visibilityThreshold = Offset.VisibilityThreshold
                    )
                )
                previousDraggingItemKey = null
            }
        }
        draggingItemDraggedDelta = Offset.Zero
        draggingItemKey = null
        draggingItemInitialOffset = previousDraggingItemInitialOffset ?: IntOffset.Zero
        scroller.tryStop()
        draggingItemTargetIndex = null
        predictedDraggingItemOffset = null
    }

    internal fun onDrag(offset: Offset) {
        draggingItemDraggedDelta += offset

        val draggingItem = draggingItemLayoutInfo ?: return
        val dragOffset = draggingItemOffset.reverseAxisIfNecessary()
            .reverseAxisWithLayoutDirectionIfLazyVerticalStaggeredGridRtlFix()
        val startOffset = draggingItem.offset.toOffset() + dragOffset
        val endOffset = startOffset + draggingItem.size.toSize()
        val (contentStartOffset, contentEndOffset) = state.layoutInfo.getScrollAreaOffsets(
            scrollThresholdPadding
        )

        val handleOffset =
            when (state.layoutInfo.reverseLayout ||
                    (layoutDirection == LayoutDirection.Rtl &&
                            orientation == Orientation.Horizontal)) {
                true -> endOffset - draggingItemHandleOffset
                false -> startOffset + draggingItemHandleOffset
            } + IntOffset.fromAxis(
                orientation,
                state.layoutInfo.beforeContentPadding
            ).toOffset()

        val distanceFromStart = (handleOffset.getAxis(orientation) - contentStartOffset)
            .coerceAtLeast(0f)
        val distanceFromEnd = (contentEndOffset - handleOffset.getAxis(orientation))
            .coerceAtLeast(0f)

        val isScrollingStarted = if (distanceFromStart < scrollThreshold) {
            scroller.start(
                Scroller.Direction.BACKWARD,
                getScrollSpeedMultiplier(distanceFromStart),
                maxScrollDistanceProvider = {
                    (draggingItemLayoutInfo?.let {
                        state.layoutInfo.mainAxisViewportSize -
                                it.offset.toOffset().getAxis(orientation) - 1f
                    }) ?: 0f
                },
                onScroll = {
                    moveDraggingItemToEnd(Scroller.Direction.BACKWARD)
                }
            )
        } else if (distanceFromEnd < scrollThreshold) {
            scroller.start(
                Scroller.Direction.FORWARD,
                getScrollSpeedMultiplier(distanceFromEnd),
                maxScrollDistanceProvider = {
                    (draggingItemLayoutInfo?.let { it ->
                        val visibleItems = state.layoutInfo.visibleItemsInfo
                        val itemBeforeDraggingItem =
                            visibleItems.getOrNull(visibleItems.indexOfFirst { it.key == draggingItemKey } - 1)
                        var itemToAlmostScrollOff = itemBeforeDraggingItem ?: it
                        var scrollDistance =
                            itemToAlmostScrollOff.offset.toOffset().getAxis(orientation) +
                                    itemToAlmostScrollOff.size.getAxis(orientation) - 1f
                        if (scrollDistance <= 0f) {
                            itemToAlmostScrollOff = it
                            scrollDistance =
                                itemToAlmostScrollOff.offset.toOffset().getAxis(orientation) +
                                        itemToAlmostScrollOff.size.getAxis(orientation) - 1f
                        }

                        scrollDistance
                    }) ?: 0f
                },
                onScroll = {
                    moveDraggingItemToEnd(Scroller.Direction.FORWARD)
                }
            )
        } else {
            scroller.tryStop()
            false
        }

        if (!onMoveStateMutex.tryLock()) return
        if (!scroller.isScrolling && !isScrollingStarted) {
            val draggingItemRect = Rect(startOffset, endOffset)
            val targetItem = findTargetItem(
                draggingItemRect,
                items = state.layoutInfo.visibleItemsInfo,
            ) {
                it.index != draggingItem.index
            }
            if (targetItem != null) {
                scope.launch {
                    moveItems(draggingItem, targetItem)
                }
            }
        }
        onMoveStateMutex.unlock()
    }

    private suspend fun moveDraggingItemToEnd(
        direction: Scroller.Direction,
    ) {
        onMoveStateMutex.lock()

        val draggingItem = draggingItemLayoutInfo
        if (draggingItem == null) {
            onMoveStateMutex.unlock()
            return
        }
        val isDraggingItemAtEnd = when (direction) {
            Scroller.Direction.FORWARD -> draggingItem.index == state.layoutInfo.visibleItemsInfo.lastOrNull()?.index
            Scroller.Direction.BACKWARD -> draggingItem.index == state.firstVisibleItemIndex
        }
        if (isDraggingItemAtEnd) {
            onMoveStateMutex.unlock()
            return
        }
        val dragOffset = draggingItemOffset.reverseAxisIfNecessary()
            .reverseAxisWithLayoutDirectionIfLazyVerticalStaggeredGridRtlFix()
        val startOffset = draggingItem.offset.toOffset() + dragOffset
        val endOffset = startOffset + draggingItem.size.toSize()
        val draggingItemRect = Rect(startOffset, endOffset).maxOutAxis(orientation)
        val targetItem = findTargetItem(
            draggingItemRect,
            items = state.layoutInfo.getItemsInContentArea(scrollThresholdPadding),
            direction.opposite,
        ) {
            it.index != state.firstVisibleItemIndex
        } ?: state.layoutInfo.getItemsInContentArea(
            scrollThresholdPadding
        ).let {
            val targetItemFunc = { item: LazyCollectionItemInfo<T> ->
                item.key in reorderableKeys && item.index != state.firstVisibleItemIndex
            }
            when (direction) {
                Scroller.Direction.FORWARD -> it.findLast(targetItemFunc)
                Scroller.Direction.BACKWARD -> it.find(targetItemFunc)
            }
        }
        val job = scope.launch {
            if (targetItem != null) {
                moveItems(draggingItem, targetItem)
            }
        }
        onMoveStateMutex.unlock()
        job.join()
    }

    private fun Rect.maxOutAxis(orientation: Orientation): Rect {
        return when (orientation) {
            Orientation.Vertical -> copy(
                top = Float.NEGATIVE_INFINITY,
                bottom = Float.POSITIVE_INFINITY,
            )

            Orientation.Horizontal -> copy(
                left = Float.NEGATIVE_INFINITY,
                right = Float.POSITIVE_INFINITY,
            )
        }
    }

    private fun findTargetItem(
        draggingItemRect: Rect,
        items: List<LazyCollectionItemInfo<T>> = state.layoutInfo.getItemsInContentArea(),
        direction: Scroller.Direction = Scroller.Direction.FORWARD,
        additionalPredicate: (LazyCollectionItemInfo<T>) -> Boolean = { true },
    ): LazyCollectionItemInfo<T>? {
        val targetItemFunc = { item: LazyCollectionItemInfo<T> ->
            val targetItemRect = Rect(item.offset.toOffset(), item.size.toSize())

            shouldItemMove(draggingItemRect, targetItemRect)
                    && item.key in reorderableKeys
                    && additionalPredicate(item)
        }
        val targetItem = when (direction) {
            Scroller.Direction.FORWARD -> items.find(targetItemFunc)
            Scroller.Direction.BACKWARD -> items.findLast(targetItemFunc)
        }
        return targetItem
    }

    private val layoutInfoFlow = snapshotFlow { state.layoutInfo }


    private suspend fun moveItems(
        draggingItem: LazyCollectionItemInfo<T>,
        targetItem: LazyCollectionItemInfo<T>,
    ) {
        if (draggingItem.index == targetItem.index) return


        val scrollToIndex = if (targetItem.index == state.firstVisibleItemIndex) {
            draggingItem.index
        } else if (draggingItem.index == state.firstVisibleItemIndex) {
            targetItem.index
        } else {
            null
        }
        if (scrollToIndex != null) {
            state.scrollToItem(scrollToIndex, state.firstVisibleItemScrollOffset)
        }

        try {
            onMoveStateMutex.withLock {
                scope.(onMoveState.value)(draggingItem.data, targetItem.data)

                predictedDraggingItemOffset = if (targetItem.index > draggingItem.index) {
                    (targetItem.offset + targetItem.size) - draggingItem.size
                } else {
                    targetItem.offset
                }
                draggingItemTargetIndex = targetItem.index

                withTimeout(1000) {
                    layoutInfoFlow.take(2).collect()
                }
            }
        } catch (e: CancellationException) {
            // do nothing
        }
    }

    internal fun isItemDragging(key: Any): State<Boolean> {
        return derivedStateOf {
            key == draggingItemKey
        }
    }

    private fun getScrollSpeedMultiplier(distance: Float): Float {
        return (1 - ((distance + scrollThreshold) / (scrollThreshold * 2)).coerceIn(
            0f,
            1f
        )) * 10
    }
}


class ReorderableCollectionItemScope(
    private val reorderableLazyCollectionState: ReorderableLazyCollectionState<*>,
    private val key: Any,
    private val itemPositionProvider: () -> Offset,
)  {
    fun Modifier.longPressDraggableHandle(
        enabled: Boolean,
        interactionSource: MutableInteractionSource? = null,
        onDragStarted: (startedPosition: Offset) -> Unit = {},
        onDragStopped: () -> Unit = {},
    ): Modifier = composed {
        var handleOffset by remember { mutableStateOf(Offset.Zero) }
        var handleSize by remember { mutableStateOf(IntSize.Zero) }

        val coroutineScope = rememberCoroutineScope()

        onGloballyPositioned {
            handleOffset = it.positionInRoot()
            handleSize = it.size
        }.longPressDraggable(
            key1 = reorderableLazyCollectionState,
            enabled = enabled && (reorderableLazyCollectionState.isItemDragging(key).value || !reorderableLazyCollectionState.isAnyItemDragging),
            interactionSource = interactionSource,
            onDragStarted = {
                coroutineScope.launch {
                    val handleOffsetRelativeToItem = handleOffset - itemPositionProvider()
                    val handleCenter = Offset(
                        handleOffsetRelativeToItem.x + handleSize.width / 2f,
                        handleOffsetRelativeToItem.y + handleSize.height / 2f
                    )

                    reorderableLazyCollectionState.onDragStart(key, handleCenter)
                }
                onDragStarted(it)
            },
            onDragStopped = {
                reorderableLazyCollectionState.onDragStop()
                onDragStopped()
            },
            onDrag = { change, dragAmount ->
                change.consume()
                reorderableLazyCollectionState.onDrag(dragAmount)
            },
        )
    }
}

@ExperimentalFoundationApi
@Composable
internal fun ReorderableCollectionItem(
    state: ReorderableLazyCollectionState<*>,
    key: Any,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    dragging: Boolean,
    content: @Composable ReorderableCollectionItemScope.(isDragging: Boolean) -> Unit,
) {
    var itemPosition by remember { mutableStateOf(Offset.Zero) }

    Box(
        modifier.onGloballyPositioned {
            itemPosition = it.positionInRoot()
        }
    ) {
        ReorderableCollectionItemScope(
            reorderableLazyCollectionState = state,
            key = key,
            itemPositionProvider = { itemPosition },
        ).content(dragging)
    }

    LaunchedEffect(state.reorderableKeys, enabled) {
        if (enabled) {
            state.reorderableKeys.add(key)
        } else {
            state.reorderableKeys.remove(key)
        }
    }
}

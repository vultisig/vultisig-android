package com.vultisig.wallet.ui.components.v2.bottomsheets

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animate
import androidx.compose.foundation.gestures.AnchoredDraggableDefaults
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.ScrollScope
import androidx.compose.foundation.gestures.anchoredDraggable
import androidx.compose.foundation.gestures.animateTo
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindowProvider
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.theme.Theme
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

internal enum class ExpandingSheetValue {
    Hidden,
    Rest,
    Full,
}

/**
 * A bottom sheet that opens short and grows into a full screen as the reader scrolls.
 *
 * It rests at a third of the window — or at [restHeight] pixels where a third is too short to show
 * the part of the content that has to be readable — so a glance costs nothing. Scrolling up spends
 * its distance on expanding the sheet before the content moves at all, and once expanded the
 * reverse holds: scrolling back to the top and continuing to pull collapses the sheet and then
 * dismisses it, so there is never a "drag it back, then tap outside" dance to leave.
 *
 * The sheet owns its scrolling so the two gestures can be handed off cleanly — [content] supplies a
 * plain column, not a scrollable one.
 *
 * Material3's own [ModalBottomSheet] can't do this: it hardcodes its partial anchor at half the
 * screen inside a private layout block, with no parameter to move it and no way to add a third
 * resting position.
 */
@Composable
internal fun ExpandingBottomSheet(
    onDismiss: () -> Unit,
    restHeight: Int,
    content: @Composable ColumnScope.() -> Unit,
) {
    val coroutineScope = rememberCoroutineScope()
    val state = remember { AnchoredDraggableState(initialValue = ExpandingSheetValue.Hidden) }
    val flingBehavior = AnchoredDraggableDefaults.flingBehavior(state)
    val scrollState = rememberScrollState()

    var windowHeight by remember { mutableIntStateOf(0) }
    var contentHeight by remember { mutableIntStateOf(0) }

    val density = LocalDensity.current

    // Expanded stops below the status bar rather than under it, so the clock never overlaps the
    // sheet's own header and the rounded top edge stays visible at every anchor.
    val expandedTop = WindowInsets.statusBars.getTop(density)
    val cutEdgeFade = with(density) { CutEdgeFadeHeight.toPx() }

    // A dialog destination that doesn't fit its decor to the system windows is themed with
    // FloatingDialogWindowTheme, which turns the platform's own dim on. It would sit under the
    // scrim below and stay at full strength while the sheet slides away, so it is cleared here —
    // during composition, which is the only moment early enough. The dialog is shown from an
    // effect, and every effect runs after this composition, so clearing it from one of those means
    // the window is added, dimmed and drawn over the screen for a frame or two first: the black
    // flash that precedes the sheet.
    val view = LocalView.current
    remember(view) { (view.parent as? DialogWindowProvider)?.window?.apply { setDimAmount(0f) } }

    LaunchedEffect(windowHeight, restHeight, expandedTop, cutEdgeFade) {
        if (windowHeight <= 0) return@LaunchedEffect
        val hiddenOffset = windowHeight.toFloat()
        // A third of the window is the resting height the design asks for. The measured content
        // only ever raises it, on a screen too short — or a font scale too large — for the part
        // that has to be readable to clear the fade within a third; it never lets the sheet open
        // taller than that just because there is more to show.
        val visibleAtRest = maxOf(hiddenOffset * RestFraction, restHeight + cutEdgeFade)
        // Where the sheet is headed has to be read before the rewind below moves it away from its
        // anchor: read afterwards, it is whichever anchor happens to be nearest the old offset, and
        // once the resting place climbs past the halfway mark of the window that is the bottom of
        // the screen — so the sheet would leave rather than grow.
        val target = state.targetValue
        val offsetBeforeUpdate = state.offset
        state.updateAnchors(
            newAnchors =
                DraggableAnchors {
                    ExpandingSheetValue.Hidden at hiddenOffset
                    ExpandingSheetValue.Rest at
                        (hiddenOffset - visibleAtRest).coerceIn(expandedTop.toFloat(), hiddenOffset)
                    ExpandingSheetValue.Full at expandedTop.toFloat()
                },
            // Holding the target instead of snapping to whichever anchor is nearest matters while
            // the sheet is still sliding in: the content measures a frame later and moves the rest
            // anchor, and at that moment the nearest anchor is still Hidden — which would abort the
            // entrance and dismiss the sheet before it ever appeared.
            newTarget = target,
        )

        // A sheet already parked on an anchor is moved there by a write with no animation behind
        // it, so a balance that loads late and wraps to a second line grows the resting height
        // under a sheet the reader is looking at and its edge jumps. That write only lands when
        // nothing else holds the sheet — an entrance, a drag or a fling keeps its own animation and
        // leaves the offset alone — so an offset that moved here is precisely the case worth
        // animating. Undoing it is synchronous and runs before this frame is laid out, so the jump
        // is never drawn; the sheet then travels the distance.
        val offsetAfterUpdate = state.offset
        if (!offsetBeforeUpdate.isNaN() && offsetAfterUpdate != offsetBeforeUpdate) {
            state.dispatchRawDelta(offsetBeforeUpdate - offsetAfterUpdate)
            state.anchoredDrag(target) { anchors, latestTarget ->
                animate(
                    initialValue = offsetBeforeUpdate,
                    targetValue = anchors.positionOf(latestTarget),
                    animationSpec = AnchoredDraggableDefaults.SnapAnimationSpec,
                ) { value, _ ->
                    dragTo(value)
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        snapshotFlow { state.anchors.hasPositionFor(ExpandingSheetValue.Rest) }.first { it }
        state.animateTo(ExpandingSheetValue.Rest)
    }

    // Settling on Hidden is the single exit: swipe, scrim tap and back press all just animate
    // there.
    LaunchedEffect(Unit) {
        snapshotFlow { state.settledValue }
            .drop(1)
            .filter { it == ExpandingSheetValue.Hidden }
            .collect { onDismiss() }
    }

    val hide = { coroutineScope.launch { state.animateTo(ExpandingSheetValue.Hidden) } }

    BackHandler { hide() }

    val closeSheet = stringResource(R.string.close_sheet_content_description)

    Box(modifier = Modifier.fillMaxSize().onSizeChanged { windowHeight = it.height }) {
        Box(
            modifier =
                Modifier.fillMaxSize()
                    .drawBehind {
                        drawRect(
                            color = Color.Black,
                            alpha = ScrimAlpha * state.visibleFraction(size.height),
                        )
                    }
                    .pointerInput(Unit) { detectTapGestures { hide() } }
                    // A tap detector answers a finger and nothing else, so the scrim needs to say
                    // what it is and carry the same dismissal as an action for a reader that
                    // dispatches one instead of touching the screen.
                    .semantics {
                        contentDescription = closeSheet
                        onClick {
                            hide()
                            true
                        }
                    }
        )

        Box(
            modifier =
                Modifier.fillMaxSize()
                    .offset {
                        val offset = state.offset
                        IntOffset(
                            x = 0,
                            y = if (offset.isNaN()) windowHeight else offset.roundToInt(),
                        )
                    }
                    .nestedScroll(
                        remember(state, flingBehavior) {
                            expandBeforeScrollingConnection(state) { velocity ->
                                state.flingToAnchor(velocity, flingBehavior)
                            }
                        }
                    )
                    .anchoredDraggable(
                        state = state,
                        orientation = Orientation.Vertical,
                        flingBehavior = flingBehavior,
                    )
        ) {
            val fadeColor = Theme.v2.colors.backgrounds.secondary
            Column(
                modifier =
                    Modifier.fillMaxSize()
                        .dottySurface()
                        .drawWithContent {
                            drawContent()
                            drawCutEdgeFade(
                                cutEdgeY = windowHeight - state.offset,
                                contentBottom = (contentHeight - scrollState.value).toFloat(),
                                alpha = state.collapsedFraction(),
                                color = fadeColor,
                            )
                        }
                        .verticalScroll(scrollState),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().onSizeChanged { contentHeight = it.height },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    content = content,
                )
            }

            DragHandler(
                modifier = Modifier.padding(top = 8.dp).align(Alignment.TopCenter),
                color = Theme.v2.colors.vibrant.primary,
            )
        }
    }
}

/**
 * Spends an upward scroll on expanding the sheet before the content scrolls, and gives whatever the
 * content leaves over on the way back — the scroll that keeps going once it is already at the top —
 * to collapsing and then dismissing it.
 */
private fun expandBeforeScrollingConnection(
    state: AnchoredDraggableState<ExpandingSheetValue>,
    onFling: suspend (velocity: Float) -> Unit,
): NestedScrollConnection =
    object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset =
            if (available.y < 0 && source == NestedScrollSource.UserInput) {
                Offset(x = 0f, y = state.dispatchRawDelta(available.y))
            } else {
                Offset.Zero
            }

        override fun onPostScroll(
            consumed: Offset,
            available: Offset,
            source: NestedScrollSource,
        ): Offset =
            if (source == NestedScrollSource.UserInput) {
                Offset(x = 0f, y = state.dispatchRawDelta(available.y))
            } else {
                Offset.Zero
            }

        override suspend fun onPreFling(available: Velocity): Velocity {
            val offset = state.offset
            return if (available.y < 0 && !offset.isNaN() && offset > state.anchors.minPosition()) {
                onFling(available.y)
                available
            } else {
                Velocity.Zero
            }
        }

        override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
            onFling(available.y)
            return available
        }
    }

/**
 * Carries a fling that started inside the content on to the sheet, landing it on an anchor.
 *
 * [AnchoredDraggableState.settle] would be the obvious way to end a gesture, but its velocity-aware
 * overload is deprecated and rejects a state built without the matching deprecated constructor —
 * and the overload that survives ignores velocity, which would mean a flick could only close the
 * sheet by travelling past the halfway point. So the fling is run through the same [FlingBehavior]
 * the drag gesture uses, which weighs velocity and position together.
 */
private suspend fun AnchoredDraggableState<ExpandingSheetValue>.flingToAnchor(
    velocity: Float,
    flingBehavior: FlingBehavior,
) {
    if (offset.isNaN()) return
    anchoredDrag { anchors ->
        val scrollScope =
            object : ScrollScope {
                override fun scrollBy(pixels: Float): Float {
                    val currentOffset = requireOffset()
                    val newOffset =
                        (currentOffset + pixels).coerceIn(
                            anchors.minPosition(),
                            anchors.maxPosition(),
                        )
                    dragTo(newOffset)
                    return newOffset - currentOffset
                }
            }
        with(flingBehavior) { scrollScope.performFling(velocity) }
    }
}

/** How much of the sheet has slid into the window, 0 when it is fully off-screen. */
private fun AnchoredDraggableState<ExpandingSheetValue>.visibleFraction(
    windowHeight: Float
): Float {
    val offset = offset
    if (offset.isNaN() || windowHeight <= 0f) return 0f
    return (1f - offset / windowHeight).coerceIn(0f, 1f)
}

/** How far the sheet still is from being fully expanded, 1 at its resting position. */
private fun AnchoredDraggableState<ExpandingSheetValue>.collapsedFraction(): Float {
    val offset = offset
    val fullOffset = anchors.positionOf(ExpandingSheetValue.Full)
    val restOffset = anchors.positionOf(ExpandingSheetValue.Rest)
    if (offset.isNaN() || fullOffset.isNaN() || restOffset.isNaN()) return 0f
    val expansionSpan = restOffset - fullOffset
    if (expansionSpan <= 0f) return 0f
    return ((offset - fullOffset) / expansionSpan).coerceIn(0f, 1f)
}

/**
 * Fades the content into the sheet background where the window cuts it off, so a sheet that has not
 * been expanded yet reads as "there is more below" rather than as a clipped view. Draws nothing
 * once the sheet is expanded, or when the content ends above the cut and there is nothing to hint
 * at.
 */
private fun ContentDrawScope.drawCutEdgeFade(
    cutEdgeY: Float,
    contentBottom: Float,
    alpha: Float,
    color: Color,
) {
    if (cutEdgeY.isNaN() || alpha <= 0f || contentBottom <= cutEdgeY) return

    val fadeTop = (cutEdgeY - CutEdgeFadeHeight.toPx()).coerceAtLeast(0f)
    val fadeHeight = cutEdgeY - fadeTop
    if (fadeHeight <= 0f) return

    drawRect(
        brush =
            Brush.verticalGradient(
                colors = listOf(Color.Transparent, color),
                startY = fadeTop,
                endY = cutEdgeY,
            ),
        topLeft = Offset(x = 0f, y = fadeTop),
        size = Size(width = size.width, height = fadeHeight),
        alpha = alpha,
    )
}

private const val ScrimAlpha = 0.32f
private const val RestFraction = 1f / 3f
private val CutEdgeFadeHeight = 48.dp

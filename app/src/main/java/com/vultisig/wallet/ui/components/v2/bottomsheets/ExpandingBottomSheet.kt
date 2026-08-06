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
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
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
import kotlinx.coroutines.coroutineScope
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
    val dragInteractions = remember { MutableInteractionSource() }

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

    // Where the sheet is headed, held here rather than read back out of the state. Asked at the
    // moment its anchors move, AnchoredDraggableState answers with whichever anchor is nearest the
    // current offset, and an offset caught under a finger or half way through an animation makes
    // that answer an accident of timing — the reading that once told a sheet which had only grown
    // taller that it was on its way out. It starts on Hidden because that is where the sheet is
    // before it slides in: name Rest any earlier and the first anchors the sheet is given put it
    // there outright, with nothing left to animate.
    var destination by remember { mutableStateOf(ExpandingSheetValue.Hidden) }

    // Two ways a reader can have hold of the sheet, neither of them visible from the state itself:
    // a drag on the sheet takes its lock but announces nothing else, and a scroll in the content
    // drives the sheet through dispatchRawDelta, which takes no lock at all.
    val isSheetDragged = dragInteractions.collectIsDraggedAsState()
    val isHeldByReader = { isSheetDragged.value || scrollState.isScrollInProgress }

    val geometry by
        rememberUpdatedState(
            SheetGeometry(
                windowHeight = windowHeight,
                restHeight = restHeight,
                expandedTop = expandedTop,
                cutEdgeFade = cutEdgeFade,
            )
        )

    LaunchedEffect(Unit) {
        // One collector rather than an effect keyed on the measurements: a resting height that
        // changes again while the sheet is still travelling to the last one waits its turn instead
        // of cutting that travel short. Cut short, the travel is cancelled between anchors and
        // leaves the sheet parked where no anchor is — the one position from which nothing can
        // work out where it was going.
        snapshotFlow { geometry }
            .filter { it.windowHeight > 0 }
            .collect { measured ->
                state.moveAnchors(
                    geometry = measured,
                    isHeldByReader = isHeldByReader,
                    destination = { destination },
                )
            }
    }

    LaunchedEffect(Unit) {
        snapshotFlow { state.anchors.hasPositionFor(ExpandingSheetValue.Rest) }.first { it }
        // Claimed before the entrance starts, so that the content measuring a frame later and
        // moving the resting anchor re-aims the entrance rather than aborting it: mid-slide the
        // nearest anchor is still the bottom of the screen, which would send the sheet back out
        // before it ever arrived.
        destination = ExpandingSheetValue.Rest
        state.animateTo(ExpandingSheetValue.Rest)
    }

    // Settling on Hidden is the single exit: swipe, scrim tap and back press all just animate
    // there. Every settle is also where the destination above is re-read from what the sheet
    // actually did, so a drag or a fling that lands somewhere new is accounted for. The first
    // value is skipped: the sheet starts settled on Hidden, which is where it is rather than where
    // it is going.
    LaunchedEffect(Unit) {
        snapshotFlow { state.settledValue }
            .drop(1)
            .collect { settled ->
                destination = settled
                if (settled == ExpandingSheetValue.Hidden) onDismiss()
            }
    }

    val hide = {
        // Named before the animation starts rather than left to the settle above: an anchor update
        // landing mid-dismissal reads the destination to decide where the sheet belongs, and a
        // stale one restarts the dismissal towards whichever anchor the sheet is still nearest.
        destination = ExpandingSheetValue.Hidden
        coroutineScope.launch { state.animateTo(ExpandingSheetValue.Hidden) }
    }

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
                        interactionSource = dragInteractions,
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

/** The measurements the sheet's three anchors are cut from. */
private data class SheetGeometry(
    val windowHeight: Int,
    val restHeight: Int,
    val expandedTop: Int,
    val cutEdgeFade: Float,
) {
    fun anchors(): DraggableAnchors<ExpandingSheetValue> {
        val hiddenOffset = windowHeight.toFloat()
        // A third of the window is the resting height the design asks for. The measured content
        // only ever raises it, on a screen too short — or a font scale too large — for the part
        // that has to be readable to clear the fade within a third; it never lets the sheet open
        // taller than that just because there is more to show.
        val visibleAtRest = maxOf(hiddenOffset * RestFraction, restHeight + cutEdgeFade)
        return DraggableAnchors {
            ExpandingSheetValue.Hidden at hiddenOffset
            ExpandingSheetValue.Rest at
                (hiddenOffset - visibleAtRest).coerceIn(expandedTop.toFloat(), hiddenOffset)
            ExpandingSheetValue.Full at expandedTop.toFloat()
        }
    }
}

/**
 * Hands the sheet the anchors [geometry] asks for and takes it along to the one it belongs on,
 * rather than leaving it where the old anchors put it.
 *
 * A sheet nothing else has hold of is moved to its new anchor by a write with no animation behind
 * it, so a balance that loads late and wraps to a second line grows the resting height under a
 * sheet the reader is looking at and its edge jumps. Undoing that write is synchronous and runs
 * before the frame it landed in is laid out, so the jump is never drawn; the sheet then travels the
 * distance instead.
 */
private suspend fun AnchoredDraggableState<ExpandingSheetValue>.moveAnchors(
    geometry: SheetGeometry,
    isHeldByReader: () -> Boolean,
    destination: () -> ExpandingSheetValue,
) {
    // Moving the anchors under a finger moves the ground the reader is standing on: the write
    // above lands, because a scroll in the content holds no lock to stop it, and the sheet is then
    // carried off to an anchor while they are still choosing one. Waiting costs nothing — the
    // height that changed is still the height applied the moment they let go.
    snapshotFlow(isHeldByReader).first { !it }

    val settlesOn = destination()
    val offsetBeforeUpdate = offset
    updateAnchors(newAnchors = geometry.anchors(), newTarget = settlesOn)
    val offsetAfterUpdate = offset
    if (offsetBeforeUpdate.isNaN() || offsetAfterUpdate == offsetBeforeUpdate) return

    dispatchRawDelta(offsetBeforeUpdate - offsetAfterUpdate)
    travelTo(settlesOn, from = offsetBeforeUpdate, abandonWhen = isHeldByReader)
}

/** Walks the sheet from [from] to where [destination] now sits, on the spec its own settles use. */
private suspend fun AnchoredDraggableState<ExpandingSheetValue>.travelTo(
    destination: ExpandingSheetValue,
    from: Float,
    abandonWhen: () -> Boolean,
) = coroutineScope {
    val travel = launch {
        anchoredDrag(destination) { anchors, latestDestination ->
            animate(
                initialValue = from,
                targetValue = anchors.positionOf(latestDestination),
                animationSpec = AnchoredDraggableDefaults.SnapAnimationSpec,
            ) { value, _ ->
                dragTo(value)
            }
        }
    }
    // A reader taking hold part way through cannot cancel this the way a competing drag on the
    // sheet would: a scroll in the content writes the offset through dispatchRawDelta, which takes
    // no lock, so the two would write over each other every frame and the animation would win.
    // Standing down is what following the finger looks like.
    val handOver = launch {
        snapshotFlow(abandonWhen).first { it }
        travel.cancel()
    }
    travel.join()
    handOver.cancel()
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

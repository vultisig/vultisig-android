package com.vultisig.wallet.ui.components.v2.bottomsheets

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.down
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.moveBy
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.test.up
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ExpandingBottomSheetTest {

    @get:Rule val compose = createComposeRule()

    /**
     * The part of the content the sheet has to show can grow after the sheet is already open and
     * resting on it — a balance loads late, or a long one wraps to a second line. Moving an idle
     * sheet to a new anchor is a write with no animation behind it, so without catching it the edge
     * teleports under the reader.
     */
    @Test
    fun aRestHeightThatGrowsAfterTheSheetSettlesWalksTheEdgeUpInsteadOfJumping() {
        var restHeight by mutableIntStateOf(ShortRestHeight)

        compose.setContent {
            ExpandingBottomSheet(onDismiss = {}, restHeight = restHeight) {
                Box(
                    modifier = Modifier.fillMaxWidth().height(LongContentHeight).testTag(ContentTag)
                )
            }
        }
        compose.waitForIdle()
        val settledEdge = sheetEdge()
        val windowHeight = compose.onRoot().fetchSemanticsNode().size.height

        compose.mainClock.autoAdvance = false
        // Taken from the window rather than fixed, so that the sheet's new resting place is further
        // from where it sits than the bottom of the screen is on every device the test runs on —
        // the geometry where growing and leaving are told apart.
        restHeight = (windowHeight * TallRestFraction).roundToInt()

        val edges =
            List(FramesSampled) {
                compose.mainClock.advanceTimeByFrame()
                compose.waitForIdle()
                sheetEdge()
            }

        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        val grownEdge = sheetEdge()

        assertTrue(
            "the sheet should have grown, but its edge went $settledEdge -> $grownEdge",
            grownEdge < settledEdge,
        )
        assertEquals(
            "the frame the taller rest height lands on must not move the edge",
            settledEdge,
            edges.first(),
            HalfPixel,
        )
        assertTrue(
            "the edge jumped to $grownEdge rather than travelling there: $edges",
            edges.distinct().size > 2,
        )
        assertTrue(
            "the edge left the span it was travelling: $edges",
            edges.all { it in grownEdge..settledEdge },
        )
    }

    /**
     * The sheet's own anchors are the ground a reader drags against, so moving them mid-gesture
     * moves the sheet out from under the finger. Nothing in AnchoredDraggableState stops it: a
     * scroll in the content drives the sheet through dispatchRawDelta, which takes no lock, so the
     * anchor update sees an idle sheet and carries it off to an anchor of its own choosing while
     * the reader is still choosing one.
     */
    @Test
    fun aRestHeightThatGrowsUnderAFingerWaitsForItToLift() {
        var restHeight by mutableIntStateOf(ShortRestHeight)

        compose.setContent {
            ExpandingBottomSheet(onDismiss = {}, restHeight = restHeight) {
                Box(
                    modifier = Modifier.fillMaxWidth().height(LongContentHeight).testTag(ContentTag)
                )
            }
        }
        compose.waitForIdle()
        val windowHeight = compose.onRoot().fetchSemanticsNode().size.height

        // Held, not released: the gesture is still open for everything that follows.
        compose.onNodeWithTag(ContentTag).performTouchInput {
            down(center)
            moveBy(Offset(x = 0f, y = -DragDistance))
        }
        compose.waitForIdle()
        val edgeUnderFinger = sheetEdge()

        restHeight = (windowHeight * TallRestFraction).roundToInt()
        repeat(FramesSampled) { compose.mainClock.advanceTimeByFrame() }
        compose.waitForIdle()

        assertEquals(
            "the sheet moved while the reader still had hold of it",
            edgeUnderFinger,
            sheetEdge(),
            HalfPixel,
        )

        compose.onNodeWithTag(ContentTag).performTouchInput { up() }
        compose.waitForIdle()

        assertTrue(
            "the taller rest height was dropped rather than deferred: edge stayed at ${sheetEdge()}",
            sheetEdge() < edgeUnderFinger,
        )
    }

    /**
     * A second change arriving while the sheet is still travelling to the first one used to cancel
     * that travel, which left the sheet parked between anchors — and the destination was then
     * inferred from that offset, so a sheet caught early enough in its journey read as being
     * closest to the bottom of the screen and left instead of arriving.
     */
    @Test
    fun aRestHeightThatChangesAgainMidTravelStillLandsOnItsAnchor() {
        var restHeight by mutableIntStateOf(ShortRestHeight)
        var dismissed = false

        compose.setContent {
            ExpandingBottomSheet(onDismiss = { dismissed = true }, restHeight = restHeight) {
                Box(
                    modifier = Modifier.fillMaxWidth().height(LongContentHeight).testTag(ContentTag)
                )
            }
        }
        compose.waitForIdle()
        val settledEdge = sheetEdge()
        val windowHeight = compose.onRoot().fetchSemanticsNode().size.height

        compose.mainClock.autoAdvance = false
        restHeight = (windowHeight * TallRestFraction).roundToInt()
        // Far enough in that the sheet has left its anchor, early enough that it is still nearer
        // the one it started from — the stretch where the old inference read the bottom of the
        // screen as the closest place to be.
        repeat(FramesIntoTravel) {
            compose.mainClock.advanceTimeByFrame()
            compose.waitForIdle()
        }
        restHeight = ShortRestHeight

        compose.mainClock.autoAdvance = true
        compose.waitForIdle()

        assertTrue("the sheet dismissed itself instead of resizing", !dismissed)
        assertEquals(
            "the sheet did not come back to rest on the height it ended up with",
            settledEdge,
            sheetEdge(),
            HalfPixel,
        )
    }

    /**
     * A travel outlives the frame it started in, so the measurement behind it can be superseded
     * while it is still under way. Seeing the old one out to the end lands the sheet on a height
     * that has already been withdrawn — visibly, since it settles there before setting off again.
     */
    @Test
    fun aRestHeightSupersededMidTravelIsDroppedRatherThanArrivedAtFirst() {
        var restHeight by mutableIntStateOf(ShortRestHeight)

        compose.setContent {
            ExpandingBottomSheet(onDismiss = {}, restHeight = restHeight) {
                Box(
                    modifier = Modifier.fillMaxWidth().height(LongContentHeight).testTag(ContentTag)
                )
            }
        }
        compose.waitForIdle()
        val settledEdge = sheetEdge()
        val windowHeight = compose.onRoot().fetchSemanticsNode().size.height

        compose.mainClock.autoAdvance = false
        restHeight = (windowHeight * TallRestFraction).roundToInt()
        repeat(FramesIntoTravel) {
            compose.mainClock.advanceTimeByFrame()
            compose.waitForIdle()
        }
        // Short of where the first change was taking it, so a sheet that sees that travel out
        // climbs past the height it ends up at rather than stopping short of it.
        restHeight = (windowHeight * MidRestFraction).roundToInt()

        val edges =
            List(FramesOfTravel) {
                compose.mainClock.advanceTimeByFrame()
                compose.waitForIdle()
                sheetEdge()
            }

        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        val grownEdge = sheetEdge()

        assertTrue(
            "the sheet should have grown, but its edge went $settledEdge -> $grownEdge",
            grownEdge < settledEdge,
        )
        assertTrue(
            "the sheet climbed to the height it had already been told to forget: $edges",
            edges.min() >= grownEdge - HalfPixel,
        )
    }

    /**
     * A fling thrown from the content keeps driving the sheet after the finger has gone, and does
     * it through a lock the scroll state says nothing about. Anchors swapped underneath it restart
     * it where it has got to with the velocity it began with, and the anchor that is next in that
     * direction is no longer the one the flick was aimed at.
     */
    @Test
    fun aRestHeightThatGrowsMidFlingDoesNotSendTheSheetPastWhereItWasThrown() {
        var restHeight by mutableIntStateOf(ShortRestHeight)
        var dismissed = false

        compose.setContent {
            ExpandingBottomSheet(onDismiss = { dismissed = true }, restHeight = restHeight) {
                // Short enough to leave the sheet nothing to scroll once it is expanded, so that
                // the content's position stays a reading of the sheet's own edge throughout.
                Box(
                    modifier =
                        Modifier.fillMaxWidth().height(ShortContentHeight).testTag(ContentTag)
                )
            }
        }
        compose.waitForIdle()
        val restEdge = sheetEdge()
        val windowHeight = compose.onRoot().fetchSemanticsNode().size.height

        compose.onNodeWithTag(ContentTag).performTouchInput { swipeUp() }
        compose.waitForIdle()
        val fullEdge = sheetEdge()
        assertTrue(
            "the sheet did not expand, so there is no fling back to test",
            fullEdge < restEdge,
        )

        // Thrown back down towards its resting place, then caught part way there. Moved in steps
        // rather than in one go: a single move is one sample, and one sample is no velocity.
        compose.onNodeWithTag(ContentTag).performTouchInput {
            down(center)
            repeat(DragSteps) { moveBy(Offset(x = 0f, y = DragDistance / DragSteps)) }
        }
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag(ContentTag).performTouchInput { up() }
        repeat(FramesIntoFling) {
            compose.mainClock.advanceTimeByFrame()
            compose.waitForIdle()
        }
        val edgeMidFling = sheetEdge()

        restHeight = (windowHeight * TallRestFraction).roundToInt()
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()

        assertTrue(
            "the fling was over before the height changed, so nothing was interrupted: " +
                "$fullEdge -> $edgeMidFling, resting at $restEdge",
            edgeMidFling > (fullEdge + restEdge) / 2f && edgeMidFling < restEdge - HalfPixel,
        )
        assertTrue("the sheet was flung off the screen rather than to its anchor", !dismissed)
        assertTrue(
            "the sheet did not end up on the taller resting height: ${sheetEdge()}",
            sheetEdge() < restEdge - HalfPixel,
        )
    }

    /**
     * The content covers the sheet and its scroll claims every touch, so the sheet has no drag
     * gesture of its own — a pull down at the top reaches it as the part of a scroll the content
     * had no use for, and that is the whole of how the sheet is closed by hand.
     */
    @Test
    fun aPullDownAtTheTopTakesTheSheetOffTheScreen() {
        var dismissed = false

        compose.setContent {
            ExpandingBottomSheet(onDismiss = { dismissed = true }, restHeight = ShortRestHeight) {
                Box(
                    modifier = Modifier.fillMaxWidth().height(LongContentHeight).testTag(ContentTag)
                )
            }
        }
        compose.waitForIdle()

        compose.onNodeWithTag(ContentTag).performTouchInput { swipeDown() }
        compose.waitForIdle()

        assertTrue("a pull down at the top left the sheet on the screen", dismissed)
    }

    /**
     * The sheet arrives by sliding up from the bottom, and the first anchors it is handed are the
     * ones it would slide along — so naming its resting place as the destination that early puts it
     * there outright, with the entrance left animating a distance of nothing.
     */
    @Test
    fun theSheetSlidesInRatherThanAppearingAtItsRestingPlace() {
        compose.mainClock.autoAdvance = false

        compose.setContent {
            ExpandingBottomSheet(onDismiss = {}, restHeight = ShortRestHeight) {
                Box(
                    modifier = Modifier.fillMaxWidth().height(LongContentHeight).testTag(ContentTag)
                )
            }
        }

        val edges =
            List(FramesOfEntrance) {
                compose.mainClock.advanceTimeByFrame()
                compose.waitForIdle()
                sheetEdge()
            }

        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        val settledEdge = sheetEdge()
        val windowHeight = compose.onRoot().fetchSemanticsNode().size.height

        val partWayIn =
            edges.count { it > settledEdge + HalfPixel && it < windowHeight - HalfPixel }
        assertTrue(
            "the sheet appeared at its resting place instead of travelling there: $edges",
            partWayIn >= 2,
        )
    }

    private fun sheetEdge(): Float =
        compose.onNodeWithTag(ContentTag).fetchSemanticsNode().positionInRoot.y
}

private const val ContentTag = "expanding-sheet-content"

// Short enough that a third of any phone window is the taller of the two and sets the anchor, then
// tall enough that the content does instead — so the resting anchor is guaranteed to move.
private const val ShortRestHeight = 200
private const val TallRestFraction = 0.75f
// Between the two, so a travel towards the tall one overshoots it and a travel dropped part way
// there does not.
private const val MidRestFraction = 0.5f

private val LongContentHeight = 2_000.dp
// Shorter than an expanded sheet on any phone the test runs on, so it never scrolls.
private val ShortContentHeight = 200.dp
private const val FramesSampled = 6
private const val FramesIntoTravel = 2
// Long enough to cover a whole travel and the start of a second one.
private const val FramesOfTravel = 30
// Far enough into the fling that the sheet is past where the taller resting height would put it.
private const val FramesIntoFling = 12
// Enough to cover composition and layout before the entrance starts, and to leave frames over
// while it is still under way.
private const val FramesOfEntrance = 10
private const val HalfPixel = 0.5f

// Past any touch slop, so the gesture is unambiguously a scroll the sheet is following.
private const val DragDistance = 200f
// Enough moves for the velocity tracker to have something to work with when the finger lifts.
private const val DragSteps = 5

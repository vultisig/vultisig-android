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
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.moveBy
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
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

private val LongContentHeight = 2_000.dp
private const val FramesSampled = 6
private const val FramesIntoTravel = 2
// Enough to cover composition and layout before the entrance starts, and to leave frames over
// while it is still under way.
private const val FramesOfEntrance = 10
private const val HalfPixel = 0.5f

// Past any touch slop, so the gesture is unambiguously a scroll the sheet is following.
private const val DragDistance = 200f

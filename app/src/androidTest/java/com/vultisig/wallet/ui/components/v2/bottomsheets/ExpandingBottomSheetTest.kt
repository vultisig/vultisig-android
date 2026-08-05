package com.vultisig.wallet.ui.components.v2.bottomsheets

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
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

        compose.mainClock.autoAdvance = false
        restHeight = TallRestHeight

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

    private fun sheetEdge(): Float =
        compose.onNodeWithTag(ContentTag).fetchSemanticsNode().positionInRoot.y
}

private const val ContentTag = "expanding-sheet-content"

// Short enough that a third of any phone window is the taller of the two and sets the anchor, then
// tall enough that the content does instead — so the resting anchor is guaranteed to move.
private const val ShortRestHeight = 200
private const val TallRestHeight = 1_200

private val LongContentHeight = 2_000.dp
private const val FramesSampled = 6
private const val HalfPixel = 0.5f

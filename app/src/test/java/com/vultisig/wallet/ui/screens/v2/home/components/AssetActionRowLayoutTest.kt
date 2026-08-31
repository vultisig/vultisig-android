package com.vultisig.wallet.ui.screens.v2.home.components

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class AssetActionRowLayoutTest {

    // A 360dp phone less the 24dp content padding the token detail sheet applies on both sides.
    private val phoneWidth = 312.dp

    // Icon box wins over every English label except "Function".
    private val fiveEnglishButtons = listOf(52.dp, 52.dp, 52.dp, 56.dp, 52.dp)

    // "Intercambiar" / "Funciones" are far wider than the icon box.
    private val fiveSpanishButtons = listOf(84.dp, 52.dp, 52.dp, 63.dp, 52.dp)

    @Test
    fun `four buttons with room to spare keep their width and the widest gap`() {
        val layout =
            calculateAssetActionRowLayout(
                availableWidth = phoneWidth,
                naturalWidths = listOf(52.dp, 52.dp, 52.dp, 52.dp),
            )

        assertEquals(AssetActionRowMaxSpacing, layout.spacing)
        assertNull(layout.buttonWidth)
    }

    @Test
    fun `five buttons close the gap instead of shrinking`() {
        val layout =
            calculateAssetActionRowLayout(
                availableWidth = phoneWidth,
                naturalWidths = fiveEnglishButtons,
            )

        // 312 - 264 = 48 spread over four gaps.
        assertEquals(12.dp, layout.spacing)
        assertNull(layout.buttonWidth)
    }

    @Test
    fun `five buttons never fall below the tightest gap`() {
        val layout =
            calculateAssetActionRowLayout(
                availableWidth = phoneWidth,
                naturalWidths = fiveSpanishButtons,
            )

        assertEquals(AssetActionRowMinSpacing, layout.spacing)
    }

    @Test
    fun `labels too wide for the row shrink every button by the same amount`() {
        val layout =
            calculateAssetActionRowLayout(
                availableWidth = phoneWidth,
                naturalWidths = fiveSpanishButtons,
            )

        // (312 - 4 * 8) / 5 — one width for all five, not four full ones and a clipped fifth.
        assertEquals(56.dp, layout.buttonWidth)
    }

    @Test
    fun `a shrunk row fits the width it was given`() {
        val layout =
            calculateAssetActionRowLayout(
                availableWidth = phoneWidth,
                naturalWidths = fiveSpanishButtons,
            )

        val buttonWidth = requireNotNull(layout.buttonWidth)
        val used =
            buttonWidth * fiveSpanishButtons.size + layout.spacing * (fiveSpanishButtons.size - 1)
        assertTrue(used <= phoneWidth, "row of $used does not fit $phoneWidth")
    }

    @Test
    fun `a single action has no gap to size`() {
        val layout =
            calculateAssetActionRowLayout(
                availableWidth = phoneWidth,
                naturalWidths = listOf(52.dp),
            )

        assertEquals(0.dp, layout.spacing)
        assertNull(layout.buttonWidth)
    }

    @Test
    fun `an empty row is not laid out`() {
        val layout =
            calculateAssetActionRowLayout(availableWidth = phoneWidth, naturalWidths = emptyList())

        assertEquals(0.dp, layout.spacing)
        assertNull(layout.buttonWidth)
    }

    @Test
    fun `a row narrower than its gaps never asks for a negative width`() {
        val layout =
            calculateAssetActionRowLayout(
                availableWidth = 16.dp,
                naturalWidths = fiveEnglishButtons,
            )

        assertEquals(0.dp, layout.buttonWidth)
    }

    @Test
    fun `an unbounded row keeps its natural widths`() {
        val layout =
            calculateAssetActionRowLayout(
                availableWidth = Dp.Infinity,
                naturalWidths = fiveEnglishButtons,
            )

        assertEquals(AssetActionRowMaxSpacing, layout.spacing)
        assertNull(layout.buttonWidth)
    }

    @Test
    fun `the caller can hold the gap below the default maximum`() {
        val layout =
            calculateAssetActionRowLayout(
                availableWidth = phoneWidth,
                naturalWidths = listOf(52.dp, 52.dp, 52.dp, 52.dp),
                maxSpacing = 12.dp,
            )

        assertEquals(12.dp, layout.spacing)
        assertNull(layout.buttonWidth)
    }
}

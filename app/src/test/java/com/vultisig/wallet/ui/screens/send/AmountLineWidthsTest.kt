package com.vultisig.wallet.ui.screens.send

import io.kotest.matchers.floats.plusOrMinus
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class AmountLineWidthsTest {

    private fun widths(textPx: Float, unitPx: Float) =
        AmountLineWidths(
            textPx = textPx,
            unitPx = unitPx,
            gapPx = GAP,
            minGapPx = MIN_GAP,
            caretPx = CARET,
        )

    @Test
    fun `a line that fits keeps full size`() {
        widths(textPx = 100f, unitPx = 50f).fitScale(availablePx = 162f) shouldBe 1f
    }

    @Test
    fun `a long line shrinks number, unit and gap together`() {
        // 0.5 × (300 + 100 + 8) + 4 = 208: the gap is exactly at its floor.
        val line = widths(textPx = 300f, unitPx = 100f)

        line.fitScale(availablePx = 208f) shouldBe (0.5f plusOrMinus TOLERANCE)
    }

    @Test
    fun `the gap stops shrinking at its floor and the number gives way instead`() {
        val line = widths(textPx = 200f, unitPx = 50f)

        val scale = line.fitScale(availablePx = 110f)

        // A scaled gap would be 3.3px; the floor of 4px is kept and the text shrinks a bit more.
        scale shouldBe (0.408f plusOrMinus TOLERANCE)
        line.lineWidth(scale) shouldBe (110f plusOrMinus TOLERANCE)
    }

    @Test
    fun `the fitted line never overruns the room, above the floor`() {
        listOf(120f, 200f, 333f, 480f).forEach { available ->
            val line = widths(textPx = 640f, unitPx = 90f)
            val scale = line.fitScale(available)
            if (scale > MIN_AMOUNT_SCALE) {
                (line.lineWidth(scale) <= available + TOLERANCE) shouldBe true
            }
        }
    }

    @Test
    fun `nothing smaller than the floor is used`() {
        widths(textPx = 2_000f, unitPx = 100f).fitScale(availablePx = 100f) shouldBe
            MIN_AMOUNT_SCALE
    }

    private companion object {
        const val GAP = 8f
        const val MIN_GAP = 4f
        const val CARET = 4f
        const val TOLERANCE = 0.01f
    }
}

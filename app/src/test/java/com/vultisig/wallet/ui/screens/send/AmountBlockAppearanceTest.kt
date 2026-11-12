package com.vultisig.wallet.ui.screens.send

import io.kotest.matchers.floats.plusOrMinus
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class AmountBlockAppearanceTest {

    @Test
    fun `a shown block is opaque at full size, a hidden one transparent at 85 percent`() {
        amountBlockAppearance(shownFraction = 1f, isReduceMotion = false) shouldBe
            AmountBlockAppearance(alpha = 1f, scale = 1f)
        amountBlockAppearance(shownFraction = 0f, isReduceMotion = false) shouldBe
            AmountBlockAppearance(alpha = 0f, scale = 0.85f)
    }

    @Test
    fun `half way through the swap both blocks are half faded and scaled`() {
        val appearance = amountBlockAppearance(shownFraction = 0.5f, isReduceMotion = false)

        appearance.alpha shouldBe 0.5f
        appearance.scale shouldBe (0.925f plusOrMinus TOLERANCE)
    }

    @Test
    fun `the spring's overshoot never pushes alpha past opaque`() {
        val appearance = amountBlockAppearance(shownFraction = 1.04f, isReduceMotion = false)

        appearance.alpha shouldBe 1f
        appearance.scale shouldBe (1.006f plusOrMinus TOLERANCE)
    }

    @Test
    fun `reduced motion only fades`() {
        amountBlockAppearance(shownFraction = 0f, isReduceMotion = true) shouldBe
            AmountBlockAppearance(alpha = 0f, scale = 1f)
        amountBlockAppearance(shownFraction = 0.3f, isReduceMotion = true).scale shouldBe 1f
    }

    private companion object {
        const val TOLERANCE = 0.0001f
    }
}

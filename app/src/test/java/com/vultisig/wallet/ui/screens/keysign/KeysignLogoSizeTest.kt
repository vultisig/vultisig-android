package com.vultisig.wallet.ui.screens.keysign

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

/**
 * Regression tests for [boundedLogoSize], the guard that prevents the keysign Rive animation from
 * being handed an oversized "toToken" logo. Rive decodes the image into an `IntArray(width *
 * height)` on the Java heap, so an unbounded logo could throw an uncatchable `OutOfMemoryError` and
 * abort the process. These tests pin the invariant that no dimension ever exceeds
 * [RIVE_TO_TOKEN_IMAGE_MAX_PX].
 */
class KeysignLogoSizeTest {

    @Test
    fun `oversized square logo is clamped to the max dimension`() {
        // The dimensions that crashed the process in the wild (~462MB IntArray).
        boundedLogoSize(10752, 10752) shouldBe
            (RIVE_TO_TOKEN_IMAGE_MAX_PX to RIVE_TO_TOKEN_IMAGE_MAX_PX)
    }

    @Test
    fun `oversized logo is clamped while preserving aspect ratio`() {
        // 1024x512 -> scale 256/1024 = 0.25 -> 256x128
        boundedLogoSize(1024, 512) shouldBe (256 to 128)
    }

    @Test
    fun `oversized logo is clamped while preserving aspect ratio when height exceeds width`() {
        // 512x1024 -> scale 256/1024 = 0.25 -> 128x256
        boundedLogoSize(512, 1024) shouldBe (128 to 256)
    }

    @Test
    fun `logo within the bound is passed through unchanged`() {
        boundedLogoSize(96, 96) shouldBe (96 to 96)
        boundedLogoSize(100, 40) shouldBe (100 to 40)
    }

    @Test
    fun `logo exactly at the bound is unchanged`() {
        boundedLogoSize(RIVE_TO_TOKEN_IMAGE_MAX_PX, RIVE_TO_TOKEN_IMAGE_MAX_PX) shouldBe
            (RIVE_TO_TOKEN_IMAGE_MAX_PX to RIVE_TO_TOKEN_IMAGE_MAX_PX)
    }

    @Test
    fun `missing intrinsic bounds fall back to a max-size square`() {
        // Vector drawables can report -1 for intrinsic width and height.
        boundedLogoSize(-1, -1) shouldBe (RIVE_TO_TOKEN_IMAGE_MAX_PX to RIVE_TO_TOKEN_IMAGE_MAX_PX)
        boundedLogoSize(0, 0) shouldBe (RIVE_TO_TOKEN_IMAGE_MAX_PX to RIVE_TO_TOKEN_IMAGE_MAX_PX)
        boundedLogoSize(256, 0) shouldBe (RIVE_TO_TOKEN_IMAGE_MAX_PX to RIVE_TO_TOKEN_IMAGE_MAX_PX)
    }

    @Test
    fun `extreme aspect ratio never collapses a dimension to zero`() {
        val (width, height) = boundedLogoSize(100_000, 1)
        width shouldBe RIVE_TO_TOKEN_IMAGE_MAX_PX
        height shouldBe 1 // coerced up from 0
    }
}

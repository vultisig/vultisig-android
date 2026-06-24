package com.vultisig.wallet.ui.screens.swap.components

import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import org.junit.jupiter.api.Test

internal class QuoteTimerProgressTest {

    private val lifetime = 1.minutes

    @Test
    fun `remaining equal to lifetime is full progress`() {
        assertEquals(1f, quoteTimerProgress(remaining = lifetime, lifetime = lifetime))
    }

    @Test
    fun `half the lifetime remaining is half progress`() {
        assertEquals(0.5f, quoteTimerProgress(remaining = 30.seconds, lifetime = lifetime))
    }

    @Test
    fun `no time remaining is zero progress`() {
        assertEquals(0f, quoteTimerProgress(remaining = 0.seconds, lifetime = lifetime))
    }

    @Test
    fun `negative remaining after expiry is clamped to zero`() {
        assertEquals(0f, quoteTimerProgress(remaining = (-5).seconds, lifetime = lifetime))
    }

    @Test
    fun `remaining beyond lifetime is clamped to one`() {
        assertEquals(1f, quoteTimerProgress(remaining = 90.seconds, lifetime = lifetime))
    }
}

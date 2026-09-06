package com.vultisig.wallet.data.keygen

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import org.junit.jupiter.api.Test

/**
 * Pins the progress-based ceremony stall clock from issue #5813.
 *
 * Three signers in far apart regions hit `timeout: failed to create vault within 60 seconds` nine
 * times in a row. The clock they tripped ran from the start of the attempt, so a ceremony that was
 * still exchanging messages — just slowly — failed anyway. These tests fix the difference: silence
 * still fails at 60 s, but applied messages keep pushing the deadline out.
 */
class CeremonyStallClockTest {

    /** Test clock in nanoseconds, advanced explicitly so no test waits on wall time. */
    private class FakeClock {
        var nanos: Long = 1_000_000_000L

        fun advance(seconds: Long) {
            nanos += seconds * 1_000_000_000L
        }
    }

    private fun clockOf(fake: FakeClock, limit: kotlin.time.Duration = 60.seconds) =
        CeremonyStallClock(limit = limit) { fake.nanos }

    @Test
    fun `a fresh clock is not stalled`() {
        val fake = FakeClock()

        assertFalse(clockOf(fake).isStalled())
    }

    @Test
    fun `silence short of the limit is not a stall`() {
        val fake = FakeClock()
        val stall = clockOf(fake)

        fake.advance(59)

        assertFalse(stall.isStalled())
    }

    @Test
    fun `silence past the limit is a stall`() {
        val fake = FakeClock()
        val stall = clockOf(fake)

        fake.advance(61)

        assertTrue(stall.isStalled())
    }

    /**
     * The fix itself. Under the old attempt-start clock this ceremony failed at 60 s despite a
     * message landing every 30 s; the deadline now moves with the last applied message.
     */
    @Test
    fun `applied messages keep a slow but healthy ceremony alive`() {
        val fake = FakeClock()
        val stall = clockOf(fake)

        repeat(10) {
            fake.advance(30)
            assertFalse(stall.isStalled(), "30 s between messages must not trip the stall")
            stall.markProgress()
        }

        // Five minutes of ceremony, none of it silent for 60 s.
        assertFalse(stall.isStalled())
    }

    @Test
    fun `the clock still fires once progress actually stops`() {
        val fake = FakeClock()
        val stall = clockOf(fake)

        fake.advance(30)
        stall.markProgress()
        fake.advance(61)

        assertTrue(stall.isStalled(), "a real stall must still fail inside the limit")
    }

    @Test
    fun `reset restarts the window for a new attempt`() {
        val fake = FakeClock()
        val stall = clockOf(fake)

        fake.advance(61)
        assertTrue(stall.isStalled())

        stall.reset()

        assertFalse(stall.isStalled(), "a retry gets a fresh peer-wait window")
    }

    @Test
    fun `sinceProgress reports the gap the failure message quotes`() {
        val fake = FakeClock()
        val stall = clockOf(fake)

        fake.advance(45)

        assertEquals(45.seconds, stall.sinceProgress())
    }

    @Test
    fun `limitSeconds exposes the configured limit`() {
        assertEquals(60L, clockOf(FakeClock()).limitSeconds)
        assertEquals(90L, clockOf(FakeClock(), limit = 90.seconds).limitSeconds)
    }
}

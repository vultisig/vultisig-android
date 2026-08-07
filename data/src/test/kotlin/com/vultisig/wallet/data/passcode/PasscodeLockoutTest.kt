package com.vultisig.wallet.data.passcode

import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import org.junit.jupiter.api.Test

internal class PasscodeLockoutTest {

    private val now = 1_000_000L

    @Test
    fun `no penalty before the attempt threshold`() {
        var state = PasscodeLockout.cleared()
        repeat(PasscodeLockout.ATTEMPTS_BEFORE_LOCKOUT - 1) {
            state = PasscodeLockout.onFailedAttempt(state, now)
        }

        assertEquals(0L, PasscodeLockout.remainingLockoutMillis(state, now))
        assertEquals(1, PasscodeLockout.remainingAttempts(state))
    }

    @Test
    fun `penalty starts at thirty seconds and escalates`() {
        val expected = listOf(30.seconds, 1.minutes, 5.minutes, 15.minutes, 60.minutes, 60.minutes)
        var state = PasscodeLockout.cleared()
        repeat(PasscodeLockout.ATTEMPTS_BEFORE_LOCKOUT - 1) {
            state = PasscodeLockout.onFailedAttempt(state, now)
        }

        expected.forEach { penalty ->
            state = PasscodeLockout.onFailedAttempt(state, now)
            assertEquals(
                penalty.inWholeMilliseconds,
                PasscodeLockout.remainingLockoutMillis(state, now),
                "after ${state.failedAttempts} failed attempts",
            )
        }
    }

    @Test
    fun `lockout expires once the penalty elapses`() {
        var state = PasscodeLockout.cleared()
        repeat(PasscodeLockout.ATTEMPTS_BEFORE_LOCKOUT) {
            state = PasscodeLockout.onFailedAttempt(state, now)
        }

        assertEquals(
            0L,
            PasscodeLockout.remainingLockoutMillis(state, now + 30.seconds.inWholeMilliseconds),
        )
    }

    @Test
    fun `winding the clock back does not shorten the lockout`() {
        var state = PasscodeLockout.cleared()
        repeat(PasscodeLockout.ATTEMPTS_BEFORE_LOCKOUT) {
            state = PasscodeLockout.onFailedAttempt(state, now)
        }

        // A device clock dragged a year into the past must not read as "penalty already served".
        val rewound = now - 365L * 24 * 60 * 60 * 1000
        assertEquals(
            30.seconds.inWholeMilliseconds,
            PasscodeLockout.remainingLockoutMillis(state, rewound),
        )
    }

    @Test
    fun `re-anchoring bounds a wound-back clock to one more penalty period`() {
        var state = PasscodeLockout.cleared()
        repeat(PasscodeLockout.ATTEMPTS_BEFORE_LOCKOUT) {
            state = PasscodeLockout.onFailedAttempt(state, now)
        }
        val rewound = now - 500_000

        // Without re-anchoring the stored instants, every later attempt reads the full penalty
        // again and the lockout never expires for as long as the clock stays behind.
        val reanchored = PasscodeLockout.reanchoredForClockChange(state, rewound)

        assertEquals(
            30.seconds.inWholeMilliseconds,
            PasscodeLockout.remainingLockoutMillis(reanchored, rewound),
        )
        assertEquals(
            0L,
            PasscodeLockout.remainingLockoutMillis(
                reanchored,
                rewound + 30.seconds.inWholeMilliseconds,
            ),
        )
    }

    @Test
    fun `re-anchoring leaves a forward-running clock untouched`() {
        var state = PasscodeLockout.cleared()
        repeat(PasscodeLockout.ATTEMPTS_BEFORE_LOCKOUT) {
            state = PasscodeLockout.onFailedAttempt(state, now)
        }

        assertSame(state, PasscodeLockout.reanchoredForClockChange(state, now + 1_000))

        // Nothing to re-anchor when no penalty is active, whichever way the clock moved.
        val cleared = PasscodeLockout.cleared()
        assertSame(cleared, PasscodeLockout.reanchoredForClockChange(cleared, now - 1_000))
    }

    @Test
    fun `remaining attempts floors at zero and clears resets everything`() {
        var state = PasscodeLockout.cleared()
        repeat(PasscodeLockout.ATTEMPTS_BEFORE_LOCKOUT + 3) {
            state = PasscodeLockout.onFailedAttempt(state, now)
        }
        assertEquals(0, PasscodeLockout.remainingAttempts(state))

        val cleared = PasscodeLockout.cleared()
        assertEquals(0, cleared.failedAttempts)
        assertEquals(0L, PasscodeLockout.remainingLockoutMillis(cleared, now))
        assertEquals(
            PasscodeLockout.ATTEMPTS_BEFORE_LOCKOUT,
            PasscodeLockout.remainingAttempts(cleared),
        )
    }
}

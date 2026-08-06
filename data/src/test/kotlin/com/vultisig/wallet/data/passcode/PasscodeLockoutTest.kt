package com.vultisig.wallet.data.passcode

import kotlin.test.assertEquals
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
        val expected =
            listOf(30.seconds, 1.minutes, 5.minutes, 15.minutes, 60.minutes, 60.minutes)
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
    fun `remaining attempts floors at zero and clears resets everything`() {
        var state = PasscodeLockout.cleared()
        repeat(PasscodeLockout.ATTEMPTS_BEFORE_LOCKOUT + 3) {
            state = PasscodeLockout.onFailedAttempt(state, now)
        }
        assertEquals(0, PasscodeLockout.remainingAttempts(state))

        val cleared = PasscodeLockout.cleared()
        assertEquals(0, cleared.failedAttempts)
        assertEquals(0L, PasscodeLockout.remainingLockoutMillis(cleared, now))
        assertEquals(PasscodeLockout.ATTEMPTS_BEFORE_LOCKOUT, PasscodeLockout.remainingAttempts(cleared))
    }
}

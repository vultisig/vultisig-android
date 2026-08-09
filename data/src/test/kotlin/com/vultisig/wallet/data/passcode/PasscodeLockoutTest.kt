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
    fun `the penalty is served by elapsed time alone`() {
        // Nothing here reads the wall clock, so moving the device clock — forwards, which used to
        // clear a penalty outright, or backwards — changes nothing about what is owed.
        var state = PasscodeLockout.cleared()
        repeat(PasscodeLockout.ATTEMPTS_BEFORE_LOCKOUT) {
            state = PasscodeLockout.onFailedAttempt(state, now)
        }

        assertEquals(
            30.seconds.inWholeMilliseconds,
            PasscodeLockout.remainingLockoutMillis(state, now),
        )
        assertEquals(
            30.seconds.inWholeMilliseconds - 1_000,
            PasscodeLockout.remainingLockoutMillis(state, now + 1_000),
        )
        assertEquals(0, PasscodeLockout.remainingAttempts(state))
    }

    @Test
    fun `a reboot leaves the whole penalty outstanding until it is re-anchored`() {
        var state = PasscodeLockout.cleared()
        repeat(PasscodeLockout.ATTEMPTS_BEFORE_LOCKOUT) {
            state = PasscodeLockout.onFailedAttempt(state, now)
        }
        // Elapsed realtime restarts from zero, the one thing that puts "now" behind the anchor.
        val afterReboot = 5_000L

        assertEquals(
            30.seconds.inWholeMilliseconds,
            PasscodeLockout.remainingLockoutMillis(state, afterReboot),
        )

        val reanchored = PasscodeLockout.reanchoredAfterReboot(state, afterReboot)

        assertEquals(
            30.seconds.inWholeMilliseconds,
            PasscodeLockout.remainingLockoutMillis(reanchored, afterReboot),
        )
        assertEquals(
            0L,
            PasscodeLockout.remainingLockoutMillis(
                reanchored,
                afterReboot + 30.seconds.inWholeMilliseconds,
            ),
        )
    }

    @Test
    fun `re-anchoring leaves a clock that has only moved forwards untouched`() {
        var state = PasscodeLockout.cleared()
        repeat(PasscodeLockout.ATTEMPTS_BEFORE_LOCKOUT) {
            state = PasscodeLockout.onFailedAttempt(state, now)
        }

        assertSame(state, PasscodeLockout.reanchoredAfterReboot(state, now + 1_000))

        // Nothing to re-anchor when no penalty is active.
        val cleared = PasscodeLockout.cleared()
        assertSame(cleared, PasscodeLockout.reanchoredAfterReboot(cleared, 0L))
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

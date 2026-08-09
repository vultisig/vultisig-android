package com.vultisig.wallet.data.passcode

import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Persisted throttling state for passcode entry.
 *
 * @param failedAttempts consecutive wrong passcodes since the last successful unlock.
 * @param penaltyMillis how long the current penalty lasts, or zero when none is active.
 * @param anchorElapsedMillis `SystemClock.elapsedRealtime` at which the current penalty began.
 */
internal data class PasscodeLockoutState(
    val failedAttempts: Int = 0,
    val penaltyMillis: Long = 0L,
    val anchorElapsedMillis: Long = 0L,
)

/**
 * Escalating delay applied to repeated wrong passcodes.
 *
 * A 6-digit passcode has only a million combinations, so an attacker who can drive the unlock
 * screen unthrottled would exhaust it quickly. The delays below put a hard ceiling on that:
 * reaching even 1% of the keyspace costs weeks of wall-clock time. State is persisted, so killing
 * and relaunching the app does not reset the penalty.
 *
 * Time is measured with `SystemClock.elapsedRealtime`, never the wall clock. Wall time is under the
 * attacker's control — winding it forward past the deadline would clear any penalty instantly — and
 * defending only the backwards direction leaves the cheaper bypass wide open. Elapsed realtime
 * cannot be set, and keeps counting through deep sleep.
 *
 * Pure and clock-injected so every branch is unit-testable without waiting.
 */
internal object PasscodeLockout {

    /** Wrong attempts allowed before the first penalty applies. */
    const val ATTEMPTS_BEFORE_LOCKOUT = 5

    private val PENALTIES = listOf(30.seconds, 1.minutes, 5.minutes, 15.minutes, 60.minutes)

    /** Cleared state, used after a successful unlock or when a new passcode is set. */
    fun cleared(): PasscodeLockoutState = PasscodeLockoutState()

    /** Wrong attempts left before the next penalty; zero once a penalty is active. */
    fun remainingAttempts(state: PasscodeLockoutState): Int =
        (ATTEMPTS_BEFORE_LOCKOUT - state.failedAttempts).coerceAtLeast(0)

    /**
     * Milliseconds the caller must wait before another attempt is accepted, or zero when entry is
     * currently allowed.
     *
     * An [elapsedRealtimeMillis] behind the anchor means the device rebooted — the only thing that
     * resets this clock — and the whole penalty is reported as outstanding until
     * [reanchoredAfterReboot] restarts it from now. Serving it in full again is deliberate: a
     * reboot is the one lever left for shortening a penalty, and it must cost more than it saves.
     */
    fun remainingLockoutMillis(state: PasscodeLockoutState, elapsedRealtimeMillis: Long): Long {
        if (state.penaltyMillis <= 0L) return 0L
        val served = elapsedRealtimeMillis - state.anchorElapsedMillis
        if (served < 0L) return state.penaltyMillis
        return (state.penaltyMillis - served).coerceAtLeast(0L)
    }

    /**
     * Restarts an active penalty at [elapsedRealtimeMillis] when the device has rebooted since it
     * began, so the penalty runs down again instead of standing forever. Returns [state] itself
     * when there is nothing to re-anchor.
     */
    fun reanchoredAfterReboot(
        state: PasscodeLockoutState,
        elapsedRealtimeMillis: Long,
    ): PasscodeLockoutState {
        if (state.penaltyMillis <= 0L || elapsedRealtimeMillis >= state.anchorElapsedMillis) {
            return state
        }
        return state.copy(anchorElapsedMillis = elapsedRealtimeMillis)
    }

    /** Returns the state after one more wrong passcode at [elapsedRealtimeMillis]. */
    fun onFailedAttempt(
        state: PasscodeLockoutState,
        elapsedRealtimeMillis: Long,
    ): PasscodeLockoutState {
        val attempts = state.failedAttempts + 1
        val penalty = penaltyFor(attempts)
        if (penalty == Duration.ZERO) {
            return state.copy(failedAttempts = attempts)
        }
        return PasscodeLockoutState(
            failedAttempts = attempts,
            penaltyMillis = penalty.inWholeMilliseconds,
            anchorElapsedMillis = elapsedRealtimeMillis,
        )
    }

    private fun penaltyFor(attempts: Int): Duration {
        if (attempts < ATTEMPTS_BEFORE_LOCKOUT) return Duration.ZERO
        val step = attempts - ATTEMPTS_BEFORE_LOCKOUT
        return PENALTIES[step.coerceAtMost(PENALTIES.lastIndex)]
    }
}

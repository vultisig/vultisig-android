package com.vultisig.wallet.data.passcode

import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Persisted throttling state for passcode entry.
 *
 * @param failedAttempts consecutive wrong passcodes since the last successful unlock.
 * @param lockedOutUntilMillis wall-clock instant at which entry is allowed again.
 * @param lockedOutAtMillis wall-clock instant at which the current penalty began, used to detect a
 *   backwards clock change.
 */
internal data class PasscodeLockoutState(
    val failedAttempts: Int = 0,
    val lockedOutUntilMillis: Long = 0L,
    val lockedOutAtMillis: Long = 0L,
)

/**
 * Escalating delay applied to repeated wrong passcodes.
 *
 * A 6-digit passcode has only a million combinations, so an attacker who can drive the unlock
 * screen unthrottled would exhaust it quickly. The delays below put a hard ceiling on that:
 * reaching even 1% of the keyspace costs weeks of wall-clock time. State is persisted, so killing
 * and relaunching the app does not reset the penalty.
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
     * If the device clock has moved backwards past the instant the penalty started, the remaining
     * time is reported as the full penalty rather than expiring early — otherwise changing the
     * system clock would be a trivial bypass. Callers persist [reanchoredForClockChange] first so
     * that penalty is then served from the moment the change was noticed and does actually run
     * down; without it the same wound-back clock would report a full penalty forever.
     */
    fun remainingLockoutMillis(state: PasscodeLockoutState, nowMillis: Long): Long {
        if (state.lockedOutUntilMillis <= 0L) return 0L
        if (nowMillis < state.lockedOutAtMillis) {
            return state.lockedOutUntilMillis - state.lockedOutAtMillis
        }
        return (state.lockedOutUntilMillis - nowMillis).coerceAtLeast(0L)
    }

    /**
     * Restarts an active penalty at [nowMillis] when the device clock has moved behind the instant
     * it began, so winding the clock back costs one more penalty period instead of an open-ended
     * lockout. Returns [state] itself when there is nothing to re-anchor.
     */
    fun reanchoredForClockChange(
        state: PasscodeLockoutState,
        nowMillis: Long,
    ): PasscodeLockoutState {
        if (state.lockedOutUntilMillis <= 0L || nowMillis >= state.lockedOutAtMillis) return state
        val penalty = state.lockedOutUntilMillis - state.lockedOutAtMillis
        return state.copy(lockedOutUntilMillis = nowMillis + penalty, lockedOutAtMillis = nowMillis)
    }

    /** Returns the state after one more wrong passcode at [nowMillis]. */
    fun onFailedAttempt(state: PasscodeLockoutState, nowMillis: Long): PasscodeLockoutState {
        val attempts = state.failedAttempts + 1
        val penalty = penaltyFor(attempts)
        if (penalty == Duration.ZERO) {
            return state.copy(failedAttempts = attempts)
        }
        return PasscodeLockoutState(
            failedAttempts = attempts,
            lockedOutUntilMillis = nowMillis + penalty.inWholeMilliseconds,
            lockedOutAtMillis = nowMillis,
        )
    }

    private fun penaltyFor(attempts: Int): Duration {
        if (attempts < ATTEMPTS_BEFORE_LOCKOUT) return Duration.ZERO
        val step = attempts - ATTEMPTS_BEFORE_LOCKOUT
        return PENALTIES[step.coerceAtMost(PENALTIES.lastIndex)]
    }
}

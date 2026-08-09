package com.vultisig.wallet.ui.models.passcode

import kotlinx.coroutines.delay

/**
 * Reports the whole seconds left of a [retryAfterMillis] lockout to [onTick], once a second, and
 * returns once the lockout has expired.
 *
 * Anchored to a deadline read from [elapsedRealtimeMillis] rather than to accumulated `delay`
 * calls. Each iteration costs a delay plus a state update, so subtracting a flat second per tick
 * lets the display run ahead of the deadline the repository is actually enforcing: the prompt
 * re-enables itself, the user submits, and the rejected attempt escalates the penalty. Seconds
 * round up for the same reason, and so that a 1,500 ms remainder shows "2 s" rather than counting
 * through "0 s".
 */
internal suspend fun countdownSeconds(
    retryAfterMillis: Long,
    elapsedRealtimeMillis: () -> Long,
    onTick: (remainingSeconds: Long) -> Unit,
) {
    val deadline = elapsedRealtimeMillis() + retryAfterMillis
    while (true) {
        val remainingMillis = deadline - elapsedRealtimeMillis()
        if (remainingMillis <= 0L) break
        onTick((remainingMillis + MILLIS_PER_SECOND - 1) / MILLIS_PER_SECOND)
        // Never sleeps past expiry: the final wait is exactly the remainder.
        delay(remainingMillis.coerceAtMost(MILLIS_PER_SECOND))
    }
}

private const val MILLIS_PER_SECOND = 1_000L

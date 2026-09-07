package com.vultisig.wallet.data.api

import kotlin.time.Duration
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Bounded retry budget for one relay `POST /message/{sessionID}`.
 *
 * Repeating the send is safe: the relay stores by `session-recipient-hash` and overwrites on
 * repeat, and receivers dedupe by the same key before applying, so a retry of a send the relay had
 * in fact accepted is a no-op rather than a duplicate round.
 *
 * The whole budget has to fit inside the ceremony stall limit. Peers keep their own clocks running
 * while this side retries, so a send that outlives the stall restarts this party while the others
 * are still waiting on it. With the defaults that is `4 x 8s + 1s + 2s + 4s` = 39 s, inside the 60
 * s stall — [worstCase] pins the arithmetic and `RelaySendBudgetTest` asserts it. Do not raise
 * [requestTimeout] back to the client default.
 *
 * @param deadlineNanos [System.nanoTime] instant this send may not outlive, or `null` for
 *   unbounded. The keysign path passes one because its poll deadline is absolute and deliberately
 *   not progress-based (`KeysignMessagePoller`, issue #5488): both the per-request timeout and the
 *   backoff sleeps are clipped to what is left of it, so a send's own retries cannot outlive the
 *   signing attempt they belong to.
 */
data class RelaySendBudget(
    val maxAttempts: Int = DEFAULT_MAX_ATTEMPTS,
    val requestTimeout: Duration = DEFAULT_REQUEST_TIMEOUT,
    val initialBackoff: Duration = DEFAULT_INITIAL_BACKOFF,
    val deadlineNanos: Long? = null,
) {
    init {
        require(maxAttempts >= 1) { "maxAttempts must be at least 1, was $maxAttempts" }
    }

    /** [initialBackoff] doubled per elapsed attempt: 1 s after the first failure, then 2 s, 4 s. */
    fun backoffAfter(attempt: Int): Duration = initialBackoff * (1 shl attempt)

    /** Every attempt timing out, plus every backoff between attempts. */
    val worstCase: Duration
        get() =
            (0 until maxAttempts - 1).fold(requestTimeout * maxAttempts) { total, attempt ->
                total + backoffAfter(attempt)
            }

    /**
     * Milliseconds this attempt's request may take: [requestTimeout], clipped to what is left of
     * [deadlineNanos]. Returns `null` when the deadline has already passed, which the caller turns
     * into a failure rather than a request nobody is still waiting for.
     */
    fun requestTimeoutMillisAt(nowNanos: Long): Long? {
        val remaining = remainingAt(nowNanos) ?: return requestTimeout.inWholeMilliseconds
        if (remaining <= Duration.ZERO) return null
        return minOf(requestTimeout, remaining).inWholeMilliseconds.coerceAtLeast(1)
    }

    /** Whether sleeping [backoff] before the next attempt still leaves time inside the deadline. */
    fun backoffFitsAt(backoff: Duration, nowNanos: Long): Boolean {
        val remaining = remainingAt(nowNanos) ?: return true
        return backoff < remaining
    }

    private fun remainingAt(nowNanos: Long): Duration? =
        deadlineNanos?.let { (it - nowNanos).nanoseconds }

    companion object {
        const val DEFAULT_MAX_ATTEMPTS = 4
        val DEFAULT_REQUEST_TIMEOUT = 8.seconds
        val DEFAULT_INITIAL_BACKOFF = 1.seconds
    }
}

/**
 * Thrown when an awaited relay send did not reach the relay: the budget above was spent, the relay
 * rejected the message outright, or the deadline expired mid-retry.
 *
 * It has its own type because the ceremony poll loops must not treat it as a failed read. Each of
 * them wraps both `getTssMessages` and the outbound round an applied inbound message triggers in
 * one broad `catch`, so an unmarked send failure is logged as "Failed to get messages" and the loop
 * keeps polling for a reply that can never arrive — the exact issue #5813 stall the awaited send
 * exists to end. `KeysignMessagePoller.poll` and the keygen `pullInboundMessages` loops rethrow
 * this ahead of that catch so the attempt restarts at once.
 */
open class RelaySendFailedException(message: String, cause: Throwable? = null) :
    Exception(message, cause)

/** Thrown when a relay send is abandoned because its ceremony deadline expired mid-retry. */
class RelaySendDeadlineExceededException(cause: Throwable? = null) :
    RelaySendFailedException("relay send abandoned: the ceremony deadline expired", cause)

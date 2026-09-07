package com.vultisig.wallet.data.keygen

import kotlin.time.Duration
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Bounds how long a keygen ceremony may go without making progress.
 *
 * The clock this replaces ran from the start of the attempt, so three signers in far apart regions
 * failed at 60 s even while their messages were still arriving and being applied — the `timeout:
 * failed to create vault within 60 seconds` of issue #5813. This one measures the gap since the
 * last *applied* inbound message, so a slow but healthy ceremony survives and a genuinely stalled
 * one still fails in 60 s.
 *
 * "Applied" rather than "arrived" is the load-bearing word. A message this device cannot clear —
 * one whose relay delete failed — is re-served by every poll, so a clock reset on arrival would
 * keep a doomed attempt alive until the relay expires the message. That is exactly why
 * [KeysignMessagePoller] keeps an absolute deadline instead (issue #5488), and why this class is
 * not used there. The keygen loops dedupe on the message hash before applying, so a re-served
 * message reaches [markProgress] only the first time.
 */
internal class CeremonyStallClock(
    private val limit: Duration = DEFAULT_LIMIT,
    private val nanoTime: () -> Long = System::nanoTime,
) {
    @Volatile private var lastProgressAt: Long = nanoTime()

    /** Seconds of silence this clock tolerates, for the caller's failure message. */
    val limitSeconds: Long
        get() = limit.inWholeSeconds

    /** Restarts the clock. Call when an attempt begins waiting on its peers. */
    fun reset() {
        lastProgressAt = nanoTime()
    }

    /** Records that this device applied inbound protocol input from a peer. */
    fun markProgress() {
        lastProgressAt = nanoTime()
    }

    /** Time since the last applied message, or since [reset] if none has been applied. */
    fun sinceProgress(): Duration = (nanoTime() - lastProgressAt).nanoseconds

    fun isStalled(): Boolean = sinceProgress() > limit

    companion object {
        val DEFAULT_LIMIT = 60.seconds
    }
}

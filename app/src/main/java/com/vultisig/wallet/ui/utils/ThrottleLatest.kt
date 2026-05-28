package com.vultisig.wallet.ui.utils

import kotlin.time.Duration
import kotlin.time.TimeMark
import kotlin.time.TimeSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Leading throttle: emits the first value immediately, then at most one value per [window], always
 * passing through any value for which [isTerminal] is true so the final state is never dropped (the
 * snapshots are cumulative, so a dropped intermediate is superseded by the next one that gets
 * through).
 *
 * Mirrors the leading debounce iOS applies to balance updates (#4337) so rows settle once per
 * window instead of reordering on every per-chain emission.
 *
 * [timeSource] is injectable so tests can drive the window deterministically; production uses a
 * monotonic wall clock rather than the coroutine scheduler so the window reflects real elapsed time
 * regardless of how fast upstream emits.
 */
internal fun <T> Flow<T>.throttleLatest(
    window: Duration,
    timeSource: TimeSource = TimeSource.Monotonic,
    isTerminal: (T) -> Boolean = { false },
): Flow<T> = flow {
    var lastEmit: TimeMark? = null
    collect { value ->
        val previous = lastEmit
        if (isTerminal(value) || previous == null || previous.elapsedNow() >= window) {
            lastEmit = timeSource.markNow()
            emit(value)
        }
    }
}

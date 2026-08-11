package com.vultisig.wallet.data.passcode

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update

/**
 * Defers auto-lock while an operation that cannot survive one is in flight.
 *
 * A keygen ceremony cannot be paused. The other devices carry on and finish whether or not this one
 * is still awake, and this device only writes its own keyshare at the very end. Lock in between and
 * the write has no data key, so the share is stored in the clear until the next unlock re-seals it.
 * Holding the lock off keeps it encrypted from the moment it is written.
 *
 * A hold does not cancel the lock, it postpones it: the timer still runs, and the lock lands as
 * soon as the last hold is released.
 */
@Singleton
class AutoLockHold @Inject constructor() {

    private val _holds = MutableStateFlow(0)

    /** Number of operations currently asking auto-lock to wait. */
    val holds: StateFlow<Int> = _holds.asStateFlow()

    /** Runs [block] with auto-lock deferred, releasing the hold however [block] ends. */
    suspend fun <T> withHold(block: suspend () -> T): T {
        _holds.update { it + 1 }
        try {
            return block()
        } finally {
            _holds.update { it - 1 }
        }
    }

    /** Suspends until nothing is holding auto-lock off. Returns immediately when nothing is. */
    suspend fun awaitRelease() {
        _holds.first { it == 0 }
    }
}

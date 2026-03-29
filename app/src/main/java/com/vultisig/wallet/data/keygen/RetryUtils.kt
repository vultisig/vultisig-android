package com.vultisig.wallet.data.keygen

import kotlinx.coroutines.delay
import timber.log.Timber

/**
 * Retries [action] up to [maxRetries] times with [delayMs] between failed attempts. Returns on
 * first success. Throws the last [IllegalStateException] if all attempts fail.
 */
internal suspend fun <T> retryWithDelay(maxRetries: Int, delayMs: Long, action: () -> T): T {
    require(maxRetries > 0) { "maxRetries must be positive, was $maxRetries" }
    lateinit var lastException: IllegalStateException
    repeat(maxRetries) { attempt ->
        try {
            return action()
        } catch (e: IllegalStateException) {
            lastException = e
            Timber.w("Retry attempt %d/%d failed: %s", attempt + 1, maxRetries, e.message)
            if (attempt < maxRetries - 1) delay(delayMs)
        }
    }
    throw lastException
}

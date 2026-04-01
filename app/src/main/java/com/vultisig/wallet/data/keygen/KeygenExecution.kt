package com.vultisig.wallet.data.keygen

import kotlin.coroutines.cancellation.CancellationException

/**
 * Relay routing for a single keygen ceremony.
 *
 * [exchangeMessageId] isolates the round-trip TSS message stream. [setupMessageId] isolates the
 * uploaded setup payload.
 *
 * These are intentionally split because standard Schnorr keygen shares the DKLS setup message, but
 * still needs an independent exchange namespace when run in parallel.
 */
internal data class KeygenRouting(val exchangeMessageId: String?, val setupMessageId: String?) {
    companion object {
        /**
         * Builds routing from optional relay namespace strings, normalizing empty values to `null`.
         */
        fun from(
            setupMessageId: String = "",
            exchangeMessageId: String = setupMessageId,
        ): KeygenRouting =
            KeygenRouting(
                exchangeMessageId = exchangeMessageId.ifEmpty { null },
                setupMessageId = setupMessageId.ifEmpty { null },
            )
    }
}

/**
 * Shared retry wrapper for keygen ceremonies.
 *
 * Normal failures retry up to [maxAttempts], while coroutine cancellation must propagate
 * immediately so sibling async jobs can be cancelled cleanly.
 */
internal suspend fun <T> runKeygenWithRetry(
    attempt: Int,
    maxAttempts: Int = 3,
    retry: suspend (nextAttempt: Int, cause: Exception) -> T,
    block: suspend () -> T,
): T =
    try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        if (attempt < maxAttempts) {
            retry(attempt + 1, e)
        } else {
            throw e
        }
    }

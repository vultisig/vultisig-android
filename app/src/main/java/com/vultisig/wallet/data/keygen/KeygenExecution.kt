package com.vultisig.wallet.data.keygen

import com.vultisig.wallet.data.models.SigningLibType
import com.vultisig.wallet.data.models.TssAction
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
 * Returns `true` when this flow should use the new feature-flagged keygen path.
 *
 * The decision is intentionally all-or-nothing for the currently supported flows:
 * - flag off: keep the legacy path everywhere
 * - reshare: always keep the legacy path
 * - flag on: opt into the new path for DKLS root keygen/migrate, key import, and MLDSA
 */
internal fun shouldUseNewKeygenExecution(
    action: TssAction,
    libType: SigningLibType,
    isParallelKeygenFeatureEnabled: Boolean,
): Boolean {
    if (action == TssAction.ReShare) {
        return false
    }

    if (isParallelKeygenFeatureEnabled) {
        return isMldsaSingleKeygen(action) ||
            isKeyImportFlow(action, libType) ||
            isDklsRootKeygen(action, libType)
    }

    return false
}

private fun isMldsaSingleKeygen(action: TssAction): Boolean = action == TssAction.SingleKeygen

private fun isKeyImportFlow(action: TssAction, libType: SigningLibType): Boolean =
    libType == SigningLibType.KeyImport && action == TssAction.KeyImport

private fun isDklsRootKeygen(action: TssAction, libType: SigningLibType): Boolean =
    libType == SigningLibType.DKLS && (action == TssAction.KEYGEN || action == TssAction.Migrate)

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

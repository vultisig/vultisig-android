package com.vultisig.wallet.data.keygen

import com.vultisig.wallet.data.models.KeyShare
import com.vultisig.wallet.data.models.SigningLibType
import com.vultisig.wallet.data.models.TssAction
import kotlin.coroutines.cancellation.CancellationException

/**
 * Relay message IDs for the batched keygen / reshare protocol. These MUST match the server's
 * `ProcessBatchKeygen` / `ProcessBatchReshare` handlers byte-for-byte, and they MUST match the
 * matching constants on iOS (`KeygenMessageId` enum in `DKLSKeygen.swift`) and Windows. Hosted here
 * as `internal const val` so the routing tests can verify them directly instead of asserting
 * tautologies against a local copy.
 */
internal const val ROOT_ECDSA_MESSAGE_ID = "p-ecdsa"
internal const val ROOT_EDDSA_MESSAGE_ID = "p-eddsa"
internal const val ROOT_ECDSA_KEY_IMPORT_MESSAGE_ID = "ecdsa_key_import"
internal const val ROOT_EDDSA_KEY_IMPORT_MESSAGE_ID = "eddsa_key_import"
internal const val ROOT_MLDSA_EXCHANGE_MESSAGE_ID = "p-mldsa"
internal const val ROOT_MLDSA_SETUP_MESSAGE_ID = "p-mldsa-setup"

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
 * - flag on: opt into the new path for DKLS root keygen/migrate, key import, MLDSA, and reshare on
 *   DKLS or KeyImport vaults (matches iOS PR #4139 + Windows PR #3753 — both treat KeyImport vaults
 *   as batch-eligible because their root keyshares are also DKLS / Schnorr).
 */
internal fun shouldUseNewKeygenExecution(
    action: TssAction,
    libType: SigningLibType,
    isParallelKeygenFeatureEnabled: Boolean,
): Boolean {
    if (!isParallelKeygenFeatureEnabled) {
        return false
    }
    return isMldsaSingleKeygen(action) ||
        isKeyImportFlow(action, libType) ||
        isDklsRootKeygen(action, libType) ||
        isBatchEligibleReshare(action, libType)
}

private fun isMldsaSingleKeygen(action: TssAction): Boolean = action == TssAction.SingleKeygen

private fun isKeyImportFlow(action: TssAction, libType: SigningLibType): Boolean =
    libType == SigningLibType.KeyImport && action == TssAction.KeyImport

private fun isDklsRootKeygen(action: TssAction, libType: SigningLibType): Boolean =
    libType == SigningLibType.DKLS && (action == TssAction.KEYGEN || action == TssAction.Migrate)

/**
 * Reshare is batch-eligible when the vault's root keyshares are DKLS (i.e. fresh DKLS vaults) or
 * KeyImport (seed-phrase imported, also using DKLS / Schnorr internally). GG20 reshare must keep
 * the legacy path because the GG20 protocol has its own qualifying ceremony semantics.
 */
internal fun isBatchEligibleReshare(action: TssAction, libType: SigningLibType): Boolean =
    action == TssAction.ReShare &&
        (libType == SigningLibType.DKLS || libType == SigningLibType.KeyImport)

/**
 * Identifies which keygen executor path runs for a given (action, libType). Pure function so the
 * dispatch matrix in `KeygenViewModel.generateKey()` can be unit-tested in isolation —
 * predicate-level tests are not enough; we need the executor decision to be exercised too.
 */
internal enum class KeygenExecutor {
    SingleKeygen,
    DklsKeygen,
    Gg20Keygen,
    KeyImportKeygen,
}

internal fun selectKeygenExecutor(action: TssAction, libType: SigningLibType): KeygenExecutor =
    when (action) {
        TssAction.SingleKeygen -> KeygenExecutor.SingleKeygen
        TssAction.ReShare ->
            // ReShare on DKLS or KeyImport vaults runs the DKLS QC ceremony (the root shares
            // are produced by the same primitives in both vault types). GG20 reshare keeps its
            // dedicated GG20 path because the legacy protocol has its own QC.
            when (libType) {
                SigningLibType.DKLS,
                SigningLibType.KeyImport -> KeygenExecutor.DklsKeygen
                SigningLibType.GG20 -> KeygenExecutor.Gg20Keygen
            }
        TssAction.KEYGEN,
        TssAction.Migrate ->
            when (libType) {
                SigningLibType.DKLS -> KeygenExecutor.DklsKeygen
                SigningLibType.GG20 -> KeygenExecutor.Gg20Keygen
                // Reachable via Migrate when navigation forces libType=DKLS, but a stray
                // KEYGEN on a KeyImport vault would land here too. Falling through to the
                // KeyImport executor matches the prior behavior; in practice the navigation
                // layer prevents this combination.
                SigningLibType.KeyImport -> KeygenExecutor.KeyImportKeygen
            }
        TssAction.KeyImport -> KeygenExecutor.KeyImportKeygen
    }

/**
 * Computes the new keyshare list for a completed root ceremony.
 *
 * For reshare we must drop the OLD root ECDSA / EdDSA shares and keep everything else (MLDSA,
 * KeyImport per-chain shares). For non-reshare flows (KEYGEN / Migrate / KeyImport / SingleKeygen)
 * the existing list is replaced with just the freshly produced root shares.
 *
 * Pure function so the preservation invariant is tested independently of the JNI-heavy ceremony
 * code in `KeygenViewModel.startKeygenDkls`. The previous inline implementation captured the
 * vault's pubkeys AFTER they were already overwritten — the filter never matched and stale root
 * shares survived. Extracting this here makes that class of bug a unit-test concern.
 */
internal fun mergeReshareKeyshares(
    existing: List<KeyShare>,
    newEcdsa: KeyShare,
    newEddsa: KeyShare,
    oldEcdsaPubKey: String,
    oldEddsaPubKey: String,
    isReshare: Boolean,
): List<KeyShare> {
    val preserved =
        if (isReshare) {
            existing.filterNot { it.pubKey == oldEcdsaPubKey || it.pubKey == oldEddsaPubKey }
        } else {
            emptyList()
        }
    return buildList {
        add(newEcdsa)
        add(newEddsa)
        addAll(preserved)
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

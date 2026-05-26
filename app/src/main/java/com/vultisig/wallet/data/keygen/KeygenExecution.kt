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
// Root ECDSA key-import setup runs on the DEFAULT relay namespace, so there is no ECDSA key-import
// setup constant; only EdDSA needs its own namespace to avoid colliding with the default ECDSA
// setup.
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
 * Per-chain relay routing for key import. Setup is always namespaced per chain; the EXCHANGE
 * channel depends on the path. Batched runs every chain concurrently, so each needs its own
 * `p-{chain}` exchange channel; sequential runs chains one at a time and shares the DEFAULT
 * exchange channel.
 *
 * The explicit empty exchange id for the sequential path is load-bearing: [KeygenRouting.from]
 * otherwise copies the setup id (the chain name) into the exchange id, making the joiner poll a
 * per-chain channel the initiator never posts to and hang. [chainRaw] MUST be `chain.raw` so the
 * namespace matches what the ceremony emits exactly (e.g. "Bitcoin", not "bitcoin").
 */
internal fun keyImportChainRouting(chainRaw: String, useParallelPath: Boolean): KeygenRouting =
    if (useParallelPath) {
        KeygenRouting.from(setupMessageId = chainRaw, exchangeMessageId = "p-$chainRaw")
    } else {
        KeygenRouting.from(setupMessageId = chainRaw, exchangeMessageId = "")
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
): Boolean = isParallelKeygenFeatureEnabled && isBatchEligibleCeremony(action, libType)

/**
 * True when the ceremony's protocol actually runs the batched / parallel path: DKLS root keygen,
 * key import, MLDSA single keygen, or a reshare on a DKLS / KeyImport vault. GG20 ceremonies are
 * never eligible, so a joiner can pin the batch opt-in off for them regardless of what a (possibly
 * forged) QR claims. This is the eligibility half of [shouldUseNewKeygenExecution], factored out so
 * the relay-routing decision can be reused as a defensive guard at the join boundary.
 */
internal fun isBatchEligibleCeremony(action: TssAction, libType: SigningLibType): Boolean =
    isMldsaSingleKeygen(action) ||
        isKeyImportFlow(action, libType) ||
        isDklsRootKeygen(action, libType) ||
        isBatchEligibleReshare(action, libType)

/**
 * Resolves whether THIS peer takes the parallel / batch path for the current ceremony.
 *
 * Reshare and key-import trust the initiator's QR opt-in EXCLUSIVELY (matches iOS
 * `KeygenViewModel.swift`): the joiner must follow the initiator's relay-namespace choice, and
 * OR-ing in a local feature flag would desync the namespaces whenever the two disagree.
 * `is_tss_batch` round-trips through `ReshareMessage` / `KeygenMessage`, so the QR carries the
 * signal for them.
 *
 * Keygen, migrate, and single-keygen keep the OR fallback: their QR does not carry the batch flag
 * (the initiator never writes it), and the FastVault server join is gated on the local
 * `isTssBatchEnabled` flag, so the initiator must use that same flag to stay in step with the
 * server's `ProcessBatchKeygen` relay namespaces. Dropping the OR here makes the app run the legacy
 * path while the server batches — the ceremony then deadlocks on mismatched channels.
 *
 * Pure function so the matrix can be unit-tested in isolation.
 */
internal fun resolveParallelKeygenOptIn(
    action: TssAction,
    qrIsTssBatch: Boolean,
    isTssBatchFeatureEnabled: Boolean,
): Boolean =
    when (action) {
        TssAction.ReShare,
        TssAction.KeyImport -> qrIsTssBatch
        TssAction.KEYGEN,
        TssAction.Migrate,
        TssAction.SingleKeygen -> isTssBatchFeatureEnabled || qrIsTssBatch
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
 * Returns `true` when a reshare ceremony must preserve the vault's existing `hexChainCode` instead
 * of adopting the DKLS QC output.
 *
 * KeyImport vaults store the mnemonic's BIP32 chaincode in `vault.hexChainCode` so the root key
 * derives per-chain addresses; the QC reshare protocol regenerates threshold shares of the same
 * secret but does not round-trip the mnemonic chaincode byte-for-byte. For freshly-created DKLS /
 * GG20 vaults the existing chaincode IS the DKLS output, so adopting the QC value is a no-op.
 * Migrate forces `libType = SigningLibType.DKLS` via navigation, so Migrate lands on `false` too.
 */
internal fun shouldKeepExistingChaincode(libType: SigningLibType): Boolean =
    libType == SigningLibType.KeyImport

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

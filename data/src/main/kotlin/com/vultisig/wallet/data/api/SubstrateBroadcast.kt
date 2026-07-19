package com.vultisig.wallet.data.api

import com.vultisig.wallet.data.api.models.cosmos.PolkadotBroadcastTransactionErrorJson
import com.vultisig.wallet.data.api.models.cosmos.PolkadotBroadcastTransactionJson

/** Thrown when a Substrate `author_submitExtrinsic` response cannot be interpreted as success. */
internal class SubstrateBroadcastException(message: String) : Exception(message)

/**
 * Shared classifier for Substrate `author_submitExtrinsic` responses (Polkadot + Bittensor).
 *
 * The JSON-RPC envelope is overloaded three ways and each must be handled distinctly, otherwise a
 * malformed body silently becomes a fabricated success:
 * - `result` present -> the node accepted the extrinsic; return its hash.
 * - idempotent error -> a peer / second signing device already submitted this extrinsic; return
 *   null so the caller resolves to the locally known hash.
 * - anything else -> unknown or failed: throw so the caller's recover-if-already-broadcast path
 *   verifies on-chain instead of reporting success blindly.
 *
 * The `(null, null)` case — neither `result` nor `error`, e.g. a truncated or proxy-mangled body
 * decoded under the production `explicitNulls = false` config — falls into the last bucket and must
 * not be reported as success.
 *
 * Both API classes share one classifier so idempotency detection cannot drift between the chains.
 */
internal object SubstrateBroadcast {

    // Substrate transaction-pool JSON-RPC error codes for an already-known / already-imported
    // extrinsic (a harmless duplicate rebroadcast).
    private val IDEMPOTENT_CODES = setOf(1012, 1013)

    // Case-insensitive message fallbacks for nodes / proxies that normalize the numeric code away
    // (e.g. Polkadot's api.vultisig.com/dot proxy) while preserving the human-readable message.
    private val IDEMPOTENT_MESSAGES =
        listOf("already imported", "already known", "temporarily banned")

    /**
     * @return the broadcast hash, or null for an idempotent duplicate.
     * @throws SubstrateBroadcastException on a node error or an uninterpretable `(null, null)`
     *   body.
     */
    fun classify(response: PolkadotBroadcastTransactionJson): String? {
        val error = response.error
        if (error != null) {
            if (isIdempotent(error)) return null
            throw SubstrateBroadcastException(
                "Substrate broadcast failed: ${error.data ?: error.message}"
            )
        }
        return response.result
            ?: throw SubstrateBroadcastException(
                "Substrate broadcast returned neither a result nor an error"
            )
    }

    private fun isIdempotent(error: PolkadotBroadcastTransactionErrorJson): Boolean {
        if (error.code in IDEMPOTENT_CODES) return true
        val message = error.message?.lowercase() ?: return false
        return IDEMPOTENT_MESSAGES.any { message.contains(it) }
    }
}

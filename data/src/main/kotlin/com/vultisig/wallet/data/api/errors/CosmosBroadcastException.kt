package com.vultisig.wallet.data.api.errors

import com.vultisig.wallet.data.api.models.cosmos.CosmosTransactionBroadcastResponse
import kotlinx.serialization.json.Json
import timber.log.Timber

/**
 * Typed broadcast failure from a Cosmos SDK node (THORChain / MayaChain).
 *
 * The [message] starts with a stable English marker so UI layers can detect the failure category
 * without unpacking the raw HTTP body: [BROADCAST_FAILURE_MARKER] for any broadcast rejection, and
 * [SEQUENCE_MISMATCH_MARKER] when the node returned `codespace=sdk`/`code=32`. The remainder of the
 * message is the node-supplied [rawLog] (a short sentence), suitable as a fallback when no
 * localized copy matches the [code].
 *
 * @param code Cosmos SDK error code returned by the node (e.g. 32 for invalid sequence).
 * @param codespace Cosmos SDK error namespace (e.g. "sdk").
 * @param rawLog Short human-readable diagnostic supplied by the node.
 * @param txHash Broadcast hash, if the node assigned one before rejecting.
 */
@Suppress("SerialVersionUIDInSerializableClass")
class CosmosBroadcastException(
    val code: Int,
    val codespace: String?,
    val rawLog: String?,
    val txHash: String?,
    message: String,
) : Exception(message) {

    /**
     * True when the node rejected the broadcast with `codespace=sdk`/`code=32` (invalid account
     * sequence). On a joined keysign device this is the duplicate-broadcast race: the peer's
     * byte-identical transaction already advanced the account sequence, so the locally computed
     * hash is the canonical on-chain hash. Any other rejection is a genuine failure.
     */
    val isSequenceMismatch: Boolean
        get() = codespace == SDK_CODESPACE && code == SDK_INVALID_SEQUENCE

    companion object {
        const val BROADCAST_FAILURE_MARKER = "broadcast_failure"
        const val SEQUENCE_MISMATCH_MARKER = "broadcast_failure:sequence_mismatch"

        private const val SDK_CODESPACE = "sdk"
        private const val SDK_INVALID_SEQUENCE = 32

        /** Build a typed broadcast exception from a parsed Cosmos `tx_response` payload. */
        fun from(
            code: Int,
            codespace: String?,
            rawLog: String?,
            txHash: String?,
        ): CosmosBroadcastException {
            val isSequenceMismatch = codespace == SDK_CODESPACE && code == SDK_INVALID_SEQUENCE
            val marker =
                if (isSequenceMismatch) SEQUENCE_MISMATCH_MARKER else BROADCAST_FAILURE_MARKER
            val detail = rawLog?.takeIf { it.isNotBlank() }?.lineSequence()?.firstOrNull().orEmpty()
            val message = if (detail.isBlank()) marker else "$marker: $detail"
            return CosmosBroadcastException(
                code = code,
                codespace = codespace,
                rawLog = rawLog,
                txHash = txHash,
                message = message,
            )
        }
    }
}

/** Cosmos SDK success code (0). */
internal const val COSMOS_TX_SUCCESS_CODE = 0

/**
 * Cosmos SDK ErrTxInMempoolCache (19): tx already accepted into mempool, treated as success.
 *
 * See https://github.com/cosmos/cosmos-sdk/blob/v0.50.0/types/errors/errors.go#L79
 */
internal const val ERR_TX_IN_MEMPOOL_CACHE = 19

/**
 * Decode a Cosmos `tx_response` body and either return the broadcast tx hash on success, or throw
 * [CosmosBroadcastException] with the parsed code/codespace/raw_log on rejection.
 *
 * Shared by `ThorChainApi`, `MayaChainApi`, and `CosmosApi` so the success-code list and the
 * rejection envelope evolve in one place. On rejection, logs `Broadcast rejected (code=%d): %s`
 * once under [logTag] before throwing.
 *
 * @param rawBody Verbatim HTTP body returned by the node's `/cosmos/tx/v1beta1/txs` endpoint.
 * @param logTag Timber tag used for the structured rejection log line.
 * @param json `Json` instance configured for the response shape.
 * @return The transaction hash when the node accepts the broadcast, or null when the node accepts
 *   it but does not assign a hash.
 */
internal fun parseCosmosBroadcastResponse(rawBody: String, logTag: String, json: Json): String? {
    val result = json.decodeFromString<CosmosTransactionBroadcastResponse>(rawBody)
    val txResponse =
        result.txResponse
            ?: run {
                Timber.tag(logTag).e("Broadcast response missing tx_response: %s", rawBody)
                throw CosmosBroadcastException.from(
                    code = -1,
                    codespace = null,
                    rawLog = rawBody,
                    txHash = null,
                )
            }
    val code = txResponse.code ?: COSMOS_TX_SUCCESS_CODE
    if (code == COSMOS_TX_SUCCESS_CODE || code == ERR_TX_IN_MEMPOOL_CACHE) {
        return txResponse.txHash
    }
    Timber.tag(logTag).e("Broadcast rejected (code=%d): %s", code, rawBody)
    throw CosmosBroadcastException.from(
        code = code,
        codespace = txResponse.codespace,
        rawLog = txResponse.rawLog,
        txHash = txResponse.txHash,
    )
}

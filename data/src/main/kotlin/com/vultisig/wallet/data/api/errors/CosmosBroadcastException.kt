package com.vultisig.wallet.data.api.errors

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

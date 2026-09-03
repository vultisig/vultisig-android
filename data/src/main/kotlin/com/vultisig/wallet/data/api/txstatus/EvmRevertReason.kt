package com.vultisig.wallet.data.api.txstatus

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * Decodes the human-readable reason an EVM node reports when a reverted transaction is replayed
 * with `eth_call`.
 *
 * Nodes disagree on where that reason goes. Geth and most hosted RPCs put the ABI-encoded revert
 * payload in `error.data` and a summary in `error.message`; others send only one of the two, and
 * some wrap the payload one level deeper (`error.data.data`, `error.data.originalError.data`). Both
 * carriers are read here so the reason survives whichever node the wallet is pointed at.
 *
 * Only `Error(string)` — the `require(cond, "reason")` encoding every aggregator's min-output check
 * uses — is decoded. A custom error or `Panic(uint256)` carries no string to recover, so the
 * message carrier is used instead and, failing that, the reason stays unknown.
 */
internal object EvmRevertReason {

    /** `keccak("Error(string)")[0..3]` — the selector prefixing a `require`/`revert` string. */
    private const val ERROR_STRING_SELECTOR = "08c379a0"

    /** One ABI word is 32 bytes, i.e. 64 hex characters. */
    private const val WORD_HEX_LENGTH = 64

    /** Longest reason kept — a runaway payload is a decode gone wrong, not a message for a user. */
    private const val MAX_REASON_LENGTH = 200

    /**
     * Node boilerplate wrapping a revert reason, longest form of each family first so the more
     * specific prefix wins. A message is only read as a reason when it carries one of these: an
     * `eth_call` that fails for an unrelated reason (a node without archive state for the block, a
     * rate limit) also arrives as `error.message`, and storing that as "why the swap reverted"
     * would be a lie.
     */
    private val MESSAGE_PREFIXES =
        listOf(
            "vm exception while processing transaction: reverted with reason string",
            "vm exception while processing transaction: revert",
            "error: transaction reverted:",
            "transaction reverted:",
            "execution reverted:",
            "execution reverted",
            "reverted:",
            "revert:",
        )

    /**
     * The revert reason carried by an `eth_call` error, or null when neither carrier holds one.
     *
     * @param message the node's `error.message`.
     * @param data the node's `error.data`, in any of the shapes described on this object.
     */
    fun decode(message: String?, data: JsonElement?): String? =
        decodeErrorString(extractRevertPayload(data)) ?: fromMessage(message)

    /**
     * Pulls the `0x…`-encoded revert payload out of an `error.data` value that may be the hex
     * string itself, or an object holding it under `data` or `originalError.data`.
     */
    private fun extractRevertPayload(data: JsonElement?): String? =
        when (data) {
            is JsonPrimitive -> data.contentOrNull
            is JsonObject ->
                (data["data"] as? JsonPrimitive)?.contentOrNull
                    ?: ((data["originalError"] as? JsonObject)?.get("data") as? JsonPrimitive)
                        ?.contentOrNull

            else -> null
        }

    /**
     * Decodes `Error(string)` calldata: the selector, then a word of offset, a word of length, and
     * the UTF-8 bytes right-padded to a word boundary. Every declared length is trusted only as far
     * as the payload actually reaches, so a truncated response yields null rather than throwing.
     */
    private fun decodeErrorString(payload: String?): String? {
        val body =
            payload
                ?.removePrefix("0x")
                ?.takeIf { it.startsWith(ERROR_STRING_SELECTOR, ignoreCase = true) }
                ?.drop(ERROR_STRING_SELECTOR.length) ?: return null
        if (body.length < WORD_HEX_LENGTH * 2) return null

        // The offset word is honoured rather than assumed: it is 0x20 for every encoder seen in the
        // wild, but reading it costs nothing and a non-standard one would otherwise decode to
        // garbage.
        val lengthStart = (body.take(WORD_HEX_LENGTH).hexToIntOrNull() ?: return null) * 2
        val stringStart = lengthStart + WORD_HEX_LENGTH
        if (stringStart > body.length) return null

        val length =
            body.substring(lengthStart, stringStart).hexToIntOrNull()?.takeIf { it > 0 }
                ?: return null
        val end = stringStart + length * 2
        if (end > body.length) return null

        return body
            .substring(stringStart, end)
            .hexToBytesOrNull()
            ?.toString(Charsets.UTF_8)
            ?.sanitize()
    }

    /**
     * The reason a node states in its message, with the node's own boilerplate stripped. Some nodes
     * put the ABI payload here rather than in `error.data`, so what is left after the prefix is
     * offered to the decoder before being taken as prose.
     */
    private fun fromMessage(message: String?): String? {
        val trimmed = message?.trim() ?: return null
        val prefix =
            MESSAGE_PREFIXES.firstOrNull { trimmed.startsWith(it, ignoreCase = true) }
                ?: return null
        val rest = trimmed.drop(prefix.length).trim()
        return decodeErrorString(rest) ?: rest.sanitize()
    }

    /**
     * Trims a decoded reason and rejects the ones that carry no information: an empty string, a
     * payload longer than any genuine `require` message, or one holding control characters — which
     * means the bytes were never UTF-8 text to begin with.
     */
    private fun String.sanitize(): String? =
        trim()
            .trim(':', '.', ' ')
            .takeIf { it.isNotEmpty() && it.length <= MAX_REASON_LENGTH }
            ?.takeIf { text -> text.none(Char::isISOControl) }

    /** Parses a hex word as a non-negative Int, rejecting anything that cannot index a payload. */
    private fun String.hexToIntOrNull(): Int? =
        toLongOrNull(radix = 16)?.takeIf { it in 0..Int.MAX_VALUE.toLong() }?.toInt()

    private fun String.hexToBytesOrNull(): ByteArray? {
        if (length % 2 != 0) return null
        return ByteArray(length / 2) { index ->
            (substring(index * 2, index * 2 + 2).toIntOrNull(radix = 16) ?: return null).toByte()
        }
    }
}

package com.vultisig.wallet.data.chains.helpers

import com.vultisig.wallet.data.common.toHex
import java.math.BigInteger

/**
 * An EVM transaction recovered from a sign-message payload whose bytes are actually a transaction
 * signing preimage, not a message. See [EvmTransactionMessageParser].
 */
data class EvmMessageTransaction(
    /** EIP-155 / typed chain id, or null for a pre-155 legacy preimage that names none. */
    val chainId: BigInteger?,
    /** 0x-prefixed 20-byte recipient, or null for contract creation (empty `to`). */
    val to: String?,
    /** Native value moved, in wei. */
    val value: BigInteger,
    /** Calldata; empty for a plain value transfer. */
    val data: ByteArray,
) {
    val dataHex: String
        get() = "0x" + data.toHex()
}

/**
 * Reads an EVM transaction out of a `personal_sign` / raw sign-message payload.
 *
 * A signed EVM transaction's digest is `keccak256` of its signing preimage, and the co-signer hashes
 * a custom message the same way with no domain separation — so a message whose bytes are a preimage
 * produces a valid transaction signature while the Verify screen shows only hex (security review
 * H4). This parser recognises that shape so the screen can say "this message is a transaction" and
 * show where the money goes, rather than leaving an opaque blob.
 *
 * Handles the three preimage encodings Ethereum uses:
 * - EIP-1559 (type 2): `0x02 ‖ rlp([chainId, nonce, maxPrio, maxFee, gas, to, value, data, access])`
 * - EIP-2930 (type 1): `0x01 ‖ rlp([chainId, nonce, gasPrice, gas, to, value, data, access])`
 * - legacy (EIP-155): `rlp([nonce, gasPrice, gas, to, value, data, chainId, 0, 0])`
 *
 * Returns null for anything that is not one of these. The strong guard against a false positive is
 * the `to` field: a real transaction's recipient is empty (creation) or exactly 20 bytes, and the
 * whole input must be consumed by one RLP item with no trailing bytes.
 */
object EvmTransactionMessageParser {

    fun parse(messageHex: String): EvmMessageTransaction? {
        val bytes = messageHex.removePrefix("0x").hexToByteArrayOrNull() ?: return null
        if (bytes.isEmpty()) return null

        return when (bytes[0].toInt() and 0xff) {
            TYPE_EIP1559 -> parseTyped(bytes, chainIdIndex = 0, toIndex = 5, minItems = 9)
            TYPE_EIP2930 -> parseTyped(bytes, chainIdIndex = 0, toIndex = 4, minItems = 8)
            else -> parseLegacy(bytes)
        }
    }

    /** A typed (0x01 / 0x02) envelope: a one-byte type then an RLP list whose layout is fixed. */
    private fun parseTyped(
        bytes: ByteArray,
        chainIdIndex: Int,
        toIndex: Int,
        minItems: Int,
    ): EvmMessageTransaction? {
        val list = (decodeWhole(bytes, offset = 1) as? Rlp.List)?.items ?: return null
        // Exactly the unsigned field count: a signed envelope appends yParity/r/s, and its bytes are
        // not a signing preimage, so it must not be read as one.
        if (list.size != minItems) return null
        val to = addressOrNull(list[toIndex]) ?: return null
        return EvmMessageTransaction(
            chainId = integer(list[chainIdIndex]).takeIf { it.signum() > 0 },
            to = to.ifEmpty { null },
            value = integer(list[toIndex + 1]),
            data = bytesOf(list[toIndex + 2]),
        )
    }

    /**
     * A legacy preimage is a bare RLP list. EIP-155 carries `[…, chainId, 0, 0]` (9 items); a
     * pre-155 preimage is the first six. The `to` is always at index 3.
     */
    private fun parseLegacy(bytes: ByteArray): EvmMessageTransaction? {
        if ((bytes[0].toInt() and 0xff) < RLP_LIST_SHORT) return null
        val list = (decodeWhole(bytes, offset = 0) as? Rlp.List)?.items ?: return null
        if (list.size != LEGACY_PRE155_ITEMS && list.size != LEGACY_EIP155_ITEMS) return null
        // An EIP-155 preimage carries `[…, chainId, 0, 0]`; a *signed* legacy tx is also nine items
        // but holds `[…, v, r, s]`. Require the trailing r/s slots empty so a signed tx is not read
        // as a preimage.
        if (list.size == LEGACY_EIP155_ITEMS && (!isEmptyString(list[7]) || !isEmptyString(list[8])))
            return null
        val to = addressOrNull(list[3]) ?: return null
        val chainId =
            if (list.size == LEGACY_EIP155_ITEMS) integer(list[6]).takeIf { it.signum() > 0 }
            else null
        return EvmMessageTransaction(
            chainId = chainId,
            to = to.ifEmpty { null },
            value = integer(list[4]),
            data = bytesOf(list[5]),
        )
    }

    /** The RLP item [bytes] encodes starting at [offset], or null unless it spans exactly to the end. */
    private fun decodeWhole(bytes: ByteArray, offset: Int): Rlp? {
        val (item, next) = decode(bytes, offset) ?: return null
        return item.takeIf { next == bytes.size }
    }

    /** The bytes of a string item, or empty for a list (callers only read strings as bytes). */
    private fun bytesOf(item: Rlp): ByteArray = (item as? Rlp.Str)?.bytes ?: ByteArray(0)

    /** True for an empty RLP string (`0x80`) — an omitted field, not a list or a value. */
    private fun isEmptyString(item: Rlp): Boolean = item is Rlp.Str && item.bytes.isEmpty()

    private fun integer(item: Rlp): BigInteger {
        val b = bytesOf(item)
        return if (b.isEmpty()) BigInteger.ZERO else BigInteger(1, b)
    }

    /** "" for an empty (creation) `to`, a 0x address for 20 bytes, null for any other length. */
    private fun addressOrNull(item: Rlp): String? {
        val b = bytesOf(item)
        return when (b.size) {
            0 -> ""
            ADDRESS_BYTES -> "0x" + b.toHex()
            else -> null
        }
    }

    private sealed interface Rlp {
        data class Str(val bytes: ByteArray) : Rlp

        data class List(val items: kotlin.collections.List<Rlp>) : Rlp
    }

    /** Decodes one RLP item at [offset], returning it and the offset just past it, or null if malformed. */
    private fun decode(bytes: ByteArray, offset: Int): Pair<Rlp, Int>? {
        if (offset !in bytes.indices) return null
        val prefix = bytes[offset].toInt() and 0xff
        return when {
            prefix < RLP_STR_SHORT -> Rlp.Str(byteArrayOf(bytes[offset])) to offset + 1

            prefix < RLP_STR_LONG -> {
                val len = prefix - RLP_STR_SHORT
                val start = offset + 1
                slice(bytes, start, len)?.let { Rlp.Str(it) to start + len }
            }

            prefix < RLP_LIST_SHORT -> {
                val lenOfLen = prefix - RLP_STR_LONG + 1
                lengthPrefixed(bytes, offset, lenOfLen)?.let { (len, start) ->
                    slice(bytes, start, len)?.let { Rlp.Str(it) to start + len }
                }
            }

            prefix < RLP_LIST_LONG -> {
                val len = prefix - RLP_LIST_SHORT
                decodeList(bytes, start = offset + 1, payloadLen = len)
            }

            else -> {
                val lenOfLen = prefix - RLP_LIST_LONG + 1
                lengthPrefixed(bytes, offset, lenOfLen)?.let { (len, start) ->
                    decodeList(bytes, start = start, payloadLen = len)
                }
            }
        }
    }

    /** Reads the big-endian length that follows a long-form prefix, and the payload's start offset. */
    private fun lengthPrefixed(bytes: ByteArray, offset: Int, lenOfLen: Int): Pair<Int, Int>? {
        val lenBytes = slice(bytes, offset + 1, lenOfLen) ?: return null
        val len = BigInteger(1, lenBytes)
        // A payload longer than the input can address is malformed; cap at Int to index safely.
        if (len.bitLength() > 31) return null
        return len.toInt() to offset + 1 + lenOfLen
    }

    private fun decodeList(bytes: ByteArray, start: Int, payloadLen: Int): Pair<Rlp, Int>? {
        val end = start + payloadLen
        if (end > bytes.size || payloadLen < 0) return null
        val items = mutableListOf<Rlp>()
        var cursor = start
        while (cursor < end) {
            val (item, next) = decode(bytes, cursor) ?: return null
            items.add(item)
            cursor = next
        }
        if (cursor != end) return null
        return Rlp.List(items) to end
    }

    private fun slice(bytes: ByteArray, start: Int, len: Int): ByteArray? {
        if (len < 0 || start < 0 || start + len > bytes.size) return null
        return bytes.copyOfRange(start, start + len)
    }

    private fun String.hexToByteArrayOrNull(): ByteArray? {
        if (length % 2 != 0) return null
        return try {
            ByteArray(length / 2) { substring(it * 2, it * 2 + 2).toInt(16).toByte() }
        } catch (_: NumberFormatException) {
            null
        }
    }

    private const val TYPE_EIP1559 = 0x02
    private const val TYPE_EIP2930 = 0x01
    private const val ADDRESS_BYTES = 20
    private const val LEGACY_PRE155_ITEMS = 6
    private const val LEGACY_EIP155_ITEMS = 9

    private const val RLP_STR_SHORT = 0x80
    private const val RLP_STR_LONG = 0xb8
    private const val RLP_LIST_SHORT = 0xc0
    private const val RLP_LIST_LONG = 0xf8
}

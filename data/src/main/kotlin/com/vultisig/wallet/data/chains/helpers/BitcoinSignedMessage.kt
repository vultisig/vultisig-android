package com.vultisig.wallet.data.chains.helpers

import com.vultisig.wallet.data.common.hexToByteArrayOrNull
import com.vultisig.wallet.data.common.isHexPrefixed
import com.vultisig.wallet.data.common.remove0x
import com.vultisig.wallet.data.common.toSha256ByteArray
import com.vultisig.wallet.data.models.Chain
import vultisig.keysign.v1.CustomMessagePayload

/**
 * A BIP-137 custom-message payload: `0x` + the hex of `0x18 || "Bitcoin Signed Message:\n" ||
 * CompactSize(len) || message`, signed over its double SHA-256. Mirrors the extension's
 * `customMessage/bitcoin.ts`.
 *
 * Only exactly one well-formed envelope is accepted, so a payload has a single reading and the
 * digest can only ever cover a message the verify screen can show. A bare 32-byte digest in
 * particular is refused, since signing one would let an initiator pick what gets signed.
 */
object BitcoinSignedMessage {

    private val ENVELOPE_PREFIX =
        byteArrayOf(0x18) + "Bitcoin Signed Message:\n".toByteArray(Charsets.UTF_8)
    private val ASCII_HEX = Regex("[0-9a-fA-F]*")

    private const val COMPACT_SIZE_U16_MARKER = 0xfd
    private const val COMPACT_SIZE_U32_MARKER = 0xfe
    private const val U16_MAX = 0xffffL

    fun isBitcoinMessage(payload: CustomMessagePayload): Boolean =
        payload.chain.equals(Chain.Bitcoin.raw, ignoreCase = true)

    fun isWellFormed(message: String): Boolean = unwrap(message) != null

    /** The lowercase hex digest to sign, or null when [message] is not one exact envelope. */
    fun digest(message: String): String? {
        val envelope = decodeHex(message) ?: return null
        if (unwrapEnvelope(envelope) == null) return null
        return envelope.toSha256ByteArray().toSha256ByteArray().toHexString()
    }

    /**
     * The message inside the envelope for display: its text, or `0x` hex when the bytes are not
     * valid UTF-8. Null when [message] is not one exact envelope.
     */
    fun displayText(message: String): String? {
        val inner = unwrap(message) ?: return null
        return try {
            inner.decodeToString(throwOnInvalidSequence = true)
        } catch (_: CharacterCodingException) {
            "0x" + inner.toHexString()
        }
    }

    private fun unwrap(message: String): ByteArray? = decodeHex(message)?.let(::unwrapEnvelope)

    /**
     * Stricter than the shared hex helpers, whose radix parsing also accepts signs and non-ASCII
     * digits.
     */
    private fun decodeHex(message: String): ByteArray? {
        if (!message.isHexPrefixed()) return null
        val hex = message.remove0x()
        if (!ASCII_HEX.matches(hex)) return null
        return hex.hexToByteArrayOrNull()
    }

    private fun unwrapEnvelope(envelope: ByteArray): ByteArray? {
        if (envelope.size <= ENVELOPE_PREFIX.size) return null
        if (!envelope.copyOf(ENVELOPE_PREFIX.size).contentEquals(ENVELOPE_PREFIX)) return null

        val lengthOffset = ENVELOPE_PREFIX.size
        val (declaredLength, lengthSize) = decodeCompactSize(envelope, lengthOffset) ?: return null
        val messageOffset = lengthOffset + lengthSize
        if (declaredLength != (envelope.size - messageOffset).toLong()) return null
        return envelope.copyOfRange(messageOffset, envelope.size)
    }

    /** Value and encoded size of the CompactSize at [offset], accepting the minimal form only. */
    private fun decodeCompactSize(bytes: ByteArray, offset: Int): Pair<Long, Int>? {
        val marker = bytes[offset].toInt() and 0xff
        val available = bytes.size - offset
        return when {
            marker < COMPACT_SIZE_U16_MARKER -> marker.toLong() to 1
            marker == COMPACT_SIZE_U16_MARKER && available >= 3 ->
                littleEndian(bytes, offset + 1, 2)
                    .takeIf { it >= COMPACT_SIZE_U16_MARKER }
                    ?.let { it to 3 }
            marker == COMPACT_SIZE_U32_MARKER && available >= 5 ->
                littleEndian(bytes, offset + 1, 4).takeIf { it > U16_MAX }?.let { it to 5 }
            else -> null
        }
    }

    private fun littleEndian(bytes: ByteArray, offset: Int, count: Int): Long =
        (0 until count).fold(0L) { value, index ->
            value or ((bytes[offset + index].toLong() and 0xff) shl (8 * index))
        }
}

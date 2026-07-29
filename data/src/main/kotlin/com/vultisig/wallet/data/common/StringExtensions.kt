package com.vultisig.wallet.data.common

import com.google.protobuf.ByteString
import com.vultisig.wallet.data.utils.Numeric
import java.math.BigInteger
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import timber.log.Timber

const val ZERO_ADDRESS = "0x0000000000000000000000000000000000000000"

fun String.toHexBytes(): ByteArray {
    return Numeric.hexStringToByteArray(this)
}

/** Parses an even-length hex string into bytes, returning null on odd length or invalid hex. */
fun String.hexToByteArrayOrNull(): ByteArray? {
    if (length % 2 != 0) return null
    return runCatching { chunked(2).map { it.toInt(16).toByte() }.toByteArray() }.getOrNull()
}

fun String.toHexByteArray(): ByteArray {
    return Numeric.hexStringToByteArray(this)
}

fun String.toByteString(): ByteString {
    return ByteString.copyFrom(this, Charsets.UTF_8)
}

fun String.toHexBytesInByteString(): ByteString {
    return ByteString.copyFrom(this.toHexBytes())
}

/**
 * Lenient hex check (`0x` optional) for memo/calldata encoding in [toByteStringOrHex] only — do not
 * use for personal_sign/message hex-vs-text gating, see [isHexPrefixed].
 */
fun String.isHex(): Boolean {
    return this.matches(Regex("^(0x)?[0-9A-Fa-f]+$"))
}

fun String.toByteStringOrHex(): ByteString {
    return if (this.isHex()) {
        this.toHexBytesInByteString()
    } else {
        this.toByteString()
    }
}

/**
 * True only for an explicit `0x` prefix — mirrors SigningHelper's signing-path check and iOS/
 * Windows, so a personal_sign message can't display differently from what gets signed (#5402).
 */
fun String.isHexPrefixed(): Boolean = startsWith("0x")

fun String.normalizeMessageFormat(): String {
    return try {
        if (this.isHexPrefixed()) {
            val hex = this.remove0x()
            // A malformed remainder (odd length, or a non-hex character) must fall back to the
            // raw string rather than decode: SigningHelper's hex-decode silently maps invalid
            // digits to garbage bytes, so decoding here too could display a clean-looking (but
            // wrong) string for content that doesn't actually represent well-formed hex.
            if (hex.length % 2 != 0 || !hex.all { it.digitToIntOrNull(16) != null }) {
                return this
            }
            val decoder =
                Charsets.UTF_8.newDecoder().apply {
                    onMalformedInput(CodingErrorAction.REPORT)
                    onUnmappableCharacter(CodingErrorAction.REPORT)
                }
            decoder
                .decode(ByteBuffer.wrap(hex.toHexBytes()))
                .toString()
                .replace("^\\p{C}+|\\p{C}+$".toRegex(), "")
        } else {
            this
        }
    } catch (e: Exception) {
        Timber.e(e, "failed to decode")
        this
    }
}

internal fun String.stripHexPrefix(): String {
    return if (startsWith("0x")) {
        substring(2)
    } else {
        this
    }
}

fun String.add0x(): String {
    if (startsWith("0x")) {
        return this
    }
    return "0x$this"
}

fun String.remove0x(): String {
    if (startsWith("0x")) {
        return removePrefix("0x")
    }
    return this
}

fun String.isNotEmptyContract(): Boolean = isNotEmpty() && !equals(ZERO_ADDRESS, ignoreCase = true)

/**
 * Parses an Ethereum JSON-RPC `QUANTITY`, which is unsigned by spec, so a signed value is malformed
 * input and yields zero like any other. The sign floor is load-bearing: `BigInteger(String, radix)`
 * accepts a leading `-`, so `"-5"` (or `"0x-5"`, since only the prefix is stripped) would otherwise
 * parse into a real negative that passes every downstream magnitude check and reaches WalletCore as
 * two's-complement bytes it reads back unsigned — `-5` encodes to `0xfb`, or 251.
 */
fun String?.convertToBigIntegerOrZero(): BigInteger {
    val cleanedInput = this?.removePrefix("0x")
    return if (cleanedInput.isNullOrEmpty()) {
        BigInteger.ZERO
    } else {
        try {
            BigInteger(cleanedInput, 16).coerceAtLeast(BigInteger.ZERO)
        } catch (_: NumberFormatException) {
            BigInteger.ZERO
        }
    }
}

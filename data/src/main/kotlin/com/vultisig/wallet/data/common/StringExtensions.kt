package com.vultisig.wallet.data.common

import com.google.protobuf.ByteString
import com.vultisig.wallet.data.utils.Numeric
import timber.log.Timber
import java.math.BigInteger

fun String.toHexBytes(): ByteArray {
    return Numeric.hexStringToByteArray(this)
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

fun String.normalizeMessageFormat(): String {
    return try {
        if (this.isHex()) {
            val hex = this.remove0x()
            if (hex.length % 2 != 0) {
                return this
            }
            val bytes = this.toHexBytes()
            String(
                bytes,
                Charsets.UTF_8
            ).replace(
                // Remove leading/trailing control characters
                "^\\p{C}+|\\p{C}+$".toRegex(),
                ""
            )
        } else {
            this
        }
    } catch (e: Exception) {
        Timber.e(
            e,
            "failed to decode"
        )
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
    if (startsWith("0x")){
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

fun String.isNotEmptyContract(): Boolean {
    val zeroAddress = "0x0000000000000000000000000000000000000000"
    return isNotEmpty() && !equals(zeroAddress, ignoreCase = true)
}

fun String?.convertToBigIntegerOrZero(): BigInteger {
    val cleanedInput = this?.removePrefix("0x")
    return if (cleanedInput.isNullOrEmpty()) {
        BigInteger.ZERO
    } else {
        try {
            BigInteger(
                cleanedInput,
                16
            )
        } catch (e: NumberFormatException) {
            BigInteger.ZERO
        }
    }
}
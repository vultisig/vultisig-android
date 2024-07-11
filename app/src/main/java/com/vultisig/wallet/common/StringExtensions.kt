package com.vultisig.wallet.common

import com.google.protobuf.ByteString
import java.math.BigInteger

fun ByteArray.toHex(): String {
    return Numeric.toHexString(this)
}

fun String.decodeFromHex(): String {
    require(length % 2 == 0) { "Must have an even length" }
    return chunked(2)
        .map { it.toInt(16).toByte() }
        .toByteArray()
        .toString(Charsets.ISO_8859_1)  // Or whichever encoding your input uses
}

fun String.encodeToHex(): String {
    return Numeric.toHexStringNoPrefix(toByteArray())
}

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

fun String?.decodeContractString(): String? {
    try {
        if (isNullOrEmpty()) return ""
        val bytes =
            removePrefix("0x")
                .chunked(2)
                .map { it.toInt(16).toByte() }
                .toByteArray()
        val length = BigInteger(bytes.sliceArray(32..63)).toInt()
        return String(bytes.sliceArray(64 until 64 + length)).lowercase()
    } catch (e: Exception) {
        return null
    }
}

fun String?.decodeContractDecimal(): Int {
    if (isNullOrEmpty()) return 0
    return BigInteger(removePrefix("0x"), 16).toInt()
}

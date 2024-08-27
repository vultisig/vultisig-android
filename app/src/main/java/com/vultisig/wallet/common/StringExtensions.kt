package com.vultisig.wallet.common

import com.google.protobuf.ByteString
import com.vultisig.wallet.data.repositories.supportedENS

fun String.decodeFromHex(): String {
    require(length % 2 == 0) { "Must have an even length" }
    return chunked(2)
        .map { it.toInt(16).toByte() }
        .toByteArray()
        .toString(Charsets.ISO_8859_1)  // Or whichever encoding your input uses
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

fun String.stripHexPrefix(): String {
    return if (startsWith("0x")) {
        substring(2)
    } else {
        this
    }
}

internal fun String.isENSNameService(): Boolean {
    return supportedENS.any { this.endsWith(it) }
}

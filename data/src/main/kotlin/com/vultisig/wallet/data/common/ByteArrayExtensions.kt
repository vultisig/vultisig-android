package com.vultisig.wallet.data.common

import com.google.protobuf.ByteString
import com.vultisig.wallet.data.utils.Numeric
import org.bouncycastle.jcajce.provider.digest.Keccak

fun ByteArray.toKeccak256(): String {
    return Numeric.toHexString(this.toKeccak256ByteArray())
}

fun ByteArray.toKeccak256ByteArray(): ByteArray {
    val digest = Keccak.Digest256()
    return digest.digest(this)
}

fun ByteArray.toByteString(): ByteString {
    return ByteString.copyFrom(this)
}

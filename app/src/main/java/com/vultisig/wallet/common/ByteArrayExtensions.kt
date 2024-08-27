package com.vultisig.wallet.common

import org.bouncycastle.jcajce.provider.digest.Keccak

internal fun ByteArray.toKeccak256(): String {
    return Numeric.toHexString(this.toKeccak256ByteArray())
}

internal fun ByteArray.toKeccak256ByteArray(): ByteArray {
    val digest = Keccak.Digest256()
    return digest.digest(this)
}
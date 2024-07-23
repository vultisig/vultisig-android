package com.vultisig.wallet.common

import org.bouncycastle.jcajce.provider.digest.Keccak

fun ByteArray.toKeccak256(): String {
    val digest = Keccak.Digest256()
    val hash = digest.digest(this)
    return Numeric.toHexString(hash)
}
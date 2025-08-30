package com.vultisig.wallet.data.utils

import com.vultisig.wallet.data.common.add0x
import java.math.BigInteger

fun BigInteger.toHexString(): String {
    return this.toString(16).add0x()
}

fun BigInteger.toSafeByteArray(): ByteArray {
    val raw = this.toByteArray()
    return ByteArray(32).apply {
        val src = if (raw.size > 32 && raw.first() == 0.toByte()) raw.drop(1).toByteArray() else raw
        require(src.size <= 32) { "Amount larger than 256 bits" }
        System.arraycopy(src, 0, this, 32 - src.size, src.size)
    }
}

fun BigInteger.increaseByPercent(percent: Int): BigInteger =
    this.multiply(BigInteger.valueOf(100L + percent)).divide(BigInteger.valueOf(100))
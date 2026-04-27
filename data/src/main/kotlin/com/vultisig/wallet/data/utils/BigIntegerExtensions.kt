package com.vultisig.wallet.data.utils

import com.vultisig.wallet.data.common.add0x
import java.math.BigDecimal
import java.math.BigInteger

fun BigInteger.toHexString(): String {
    return this.toString(16).add0x()
}

/**
 * Hex string with `0x` prefix and an even number of hex digits.
 *
 * Some RPC endpoints (Blockaid's `eth_sendTransaction` simulator in particular) reject odd-length
 * hex even though `BigInteger.toString(16)` happily produces `"a"` for the value 10. This pads with
 * a leading zero to guarantee an even digit count after the `0x` prefix.
 */
fun BigInteger.toEvenLengthHexString(): String {
    val hex = this.toString(16)
    val padded = if (hex.length % 2 == 1) "0$hex" else hex
    return padded.add0x()
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

fun BigInteger.toValue(decimals: Int): BigDecimal =
    this.toBigDecimal().divide(BigDecimal.TEN.pow(decimals))

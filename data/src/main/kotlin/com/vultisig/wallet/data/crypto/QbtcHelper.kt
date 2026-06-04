@file:OptIn(ExperimentalStdlibApi::class)

package com.vultisig.wallet.data.crypto

import java.security.MessageDigest

object QbtcHelper {

    private const val BECH32_CHARSET = "qpzry9x8gf2tvdw0s3jn54khce6mua7l"

    fun deriveAddress(mldsaPubKeyHex: String): String {
        val pubKeyBytes = mldsaPubKeyHex.hexToByteArray()
        val sha256 = MessageDigest.getInstance("SHA-256").digest(pubKeyBytes)
        val ripemd160 = Ripemd160.hash(sha256)
        return bech32Encode("qbtc", convertBits(ripemd160, 8, 5, true))
    }

    private fun convertBits(data: ByteArray, fromBits: Int, toBits: Int, pad: Boolean): ByteArray {
        var acc = 0
        var bits = 0
        val maxv = (1 shl toBits) - 1
        val result = mutableListOf<Byte>()
        for (b in data) {
            val value = b.toInt() and 0xFF
            acc = (acc shl fromBits) or value
            bits += fromBits
            while (bits >= toBits) {
                bits -= toBits
                result.add(((acc shr bits) and maxv).toByte())
            }
        }
        if (pad && bits > 0) {
            result.add(((acc shl (toBits - bits)) and maxv).toByte())
        }
        return result.toByteArray()
    }

    private fun bech32Encode(hrp: String, data: ByteArray): String {
        val values = data.map { it.toInt() and 0xFF }
        val combined = values + bech32CreateChecksum(hrp, values)
        return buildString {
            append(hrp)
            append('1')
            combined.forEach { append(BECH32_CHARSET[it]) }
        }
    }

    private fun bech32CreateChecksum(hrp: String, data: List<Int>): List<Int> {
        val values = bech32HrpExpand(hrp) + data + List(6) { 0 }
        val polymod = bech32Polymod(values) xor 1
        return (0 until 6).map { (polymod shr (5 * (5 - it))) and 31 }
    }

    private fun bech32HrpExpand(hrp: String): List<Int> =
        hrp.map { it.code shr 5 } + 0 + hrp.map { it.code and 31 }

    private fun bech32Polymod(values: List<Int>): Int {
        val gen = intArrayOf(0x3b6a57b2, 0x26508e6d, 0x1ea119fa, 0x3d4233dd, 0x2a1462b3)
        var chk = 1
        for (v in values) {
            val top = chk shr 25
            chk = ((chk and 0x1ffffff) shl 5) xor v
            gen.forEachIndexed { i, g ->
                if ((top shr i) and 1 != 0) {
                    chk = chk xor g
                }
            }
        }
        return chk
    }
}

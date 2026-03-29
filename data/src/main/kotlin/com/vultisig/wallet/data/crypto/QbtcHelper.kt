@file:OptIn(ExperimentalStdlibApi::class)

package com.vultisig.wallet.data.crypto

import java.security.MessageDigest

object QbtcHelper {

    private const val BECH32_CHARSET = "qpzry9x8gf2tvdw0s3jn54khce6mua7l"

    fun deriveAddress(mldsaPubKeyHex: String): String {
        val pubKeyBytes = mldsaPubKeyHex.hexToByteArray()
        val sha256 = MessageDigest.getInstance("SHA-256").digest(pubKeyBytes)
        val ripemd160 = ripemd160(sha256)
        return bech32Encode("qbtc", convertBits(ripemd160, 8, 5, true))
    }

    private fun ripemd160(data: ByteArray): ByteArray {
        // Pure Kotlin RIPEMD-160 — Android doesn't include it in default security providers
        var h0 = 0x67452301
        var h1 = 0xEFCDAB89.toInt()
        var h2 = 0x98BADCFE.toInt()
        var h3 = 0x10325476
        var h4 = 0xC3D2E1F0.toInt()

        val msgLen = data.size
        val bitLen = msgLen.toLong() * 8
        // padding: 1 byte 0x80, then zeros, then 8-byte little-endian length
        val padLen = (55 - msgLen % 64 + 64) % 64 + 1
        val padded = ByteArray(msgLen + padLen + 8)
        System.arraycopy(data, 0, padded, 0, msgLen)
        padded[msgLen] = 0x80.toByte()
        for (i in 0 until 8) padded[padded.size - 8 + i] = (bitLen ushr (8 * i)).toByte()

        fun Int.rotl(n: Int) = (this shl n) or (this ushr (32 - n))
        fun getLE(buf: ByteArray, off: Int) =
            (buf[off].toInt() and 0xFF) or
                ((buf[off + 1].toInt() and 0xFF) shl 8) or
                ((buf[off + 2].toInt() and 0xFF) shl 16) or
                ((buf[off + 3].toInt() and 0xFF) shl 24)

        val r =
            intArrayOf(
                0,
                1,
                2,
                3,
                4,
                5,
                6,
                7,
                8,
                9,
                10,
                11,
                12,
                13,
                14,
                15,
                7,
                4,
                13,
                1,
                10,
                6,
                15,
                3,
                12,
                0,
                9,
                5,
                2,
                14,
                11,
                8,
                3,
                10,
                14,
                4,
                9,
                15,
                8,
                1,
                2,
                7,
                0,
                6,
                13,
                11,
                5,
                12,
                1,
                9,
                11,
                10,
                0,
                8,
                12,
                4,
                13,
                3,
                7,
                15,
                14,
                5,
                6,
                2,
                4,
                0,
                5,
                9,
                7,
                12,
                2,
                10,
                14,
                1,
                3,
                8,
                11,
                6,
                15,
                13,
            )
        val rr =
            intArrayOf(
                5,
                14,
                7,
                0,
                9,
                2,
                11,
                4,
                13,
                6,
                15,
                8,
                1,
                10,
                3,
                12,
                6,
                11,
                3,
                7,
                0,
                13,
                5,
                10,
                14,
                15,
                8,
                12,
                4,
                9,
                1,
                2,
                15,
                5,
                1,
                3,
                7,
                14,
                6,
                9,
                11,
                8,
                12,
                2,
                10,
                0,
                4,
                13,
                8,
                6,
                4,
                1,
                3,
                11,
                15,
                0,
                5,
                12,
                2,
                13,
                9,
                7,
                10,
                14,
                12,
                15,
                10,
                4,
                1,
                5,
                8,
                7,
                6,
                2,
                13,
                14,
                0,
                3,
                9,
                11,
            )
        val s =
            intArrayOf(
                11,
                14,
                15,
                12,
                5,
                8,
                7,
                9,
                11,
                13,
                14,
                15,
                6,
                7,
                9,
                8,
                7,
                6,
                8,
                13,
                11,
                9,
                7,
                15,
                7,
                12,
                15,
                9,
                11,
                7,
                13,
                12,
                11,
                13,
                6,
                7,
                14,
                9,
                13,
                15,
                14,
                8,
                13,
                6,
                5,
                12,
                7,
                5,
                11,
                12,
                14,
                15,
                14,
                15,
                9,
                8,
                9,
                14,
                5,
                6,
                8,
                6,
                5,
                12,
                9,
                15,
                5,
                11,
                6,
                8,
                13,
                12,
                5,
                12,
                13,
                14,
                11,
                8,
                5,
                6,
            )
        val ss =
            intArrayOf(
                8,
                9,
                9,
                11,
                13,
                15,
                15,
                5,
                7,
                7,
                8,
                11,
                14,
                14,
                12,
                6,
                9,
                13,
                15,
                7,
                12,
                8,
                9,
                11,
                7,
                7,
                12,
                7,
                6,
                15,
                13,
                11,
                9,
                7,
                15,
                11,
                8,
                6,
                6,
                14,
                12,
                13,
                5,
                14,
                13,
                13,
                7,
                5,
                15,
                5,
                8,
                11,
                14,
                14,
                6,
                14,
                6,
                9,
                12,
                9,
                12,
                5,
                15,
                8,
                8,
                5,
                12,
                9,
                12,
                5,
                14,
                6,
                8,
                13,
                6,
                5,
                15,
                13,
                11,
                11,
            )

        for (i in padded.indices step 64) {
            val x = IntArray(16) { getLE(padded, i + it * 4) }
            var al = h0
            var bl = h1
            var cl = h2
            var dl = h3
            var el = h4
            var ar = h0
            var br = h1
            var cr = h2
            var dr = h3
            var er = h4

            for (j in 0 until 80) {
                val fl: Int
                val kl: Int
                when (j / 16) {
                    0 -> {
                        fl = bl xor cl xor dl
                        kl = 0x00000000
                    }
                    1 -> {
                        fl = (bl and cl) or (bl.inv() and dl)
                        kl = 0x5A827999
                    }
                    2 -> {
                        fl = (bl or cl.inv()) xor dl
                        kl = 0x6ED9EBA1
                    }
                    3 -> {
                        fl = (bl and dl) or (cl and dl.inv())
                        kl = 0x8F1BBCDC.toInt()
                    }
                    else -> {
                        fl = bl xor (cl or dl.inv())
                        kl = 0xA953FD4E.toInt()
                    }
                }
                var tl = al + fl + x[r[j]] + kl
                tl = tl.rotl(s[j]) + el
                al = el
                el = dl
                dl = cl.rotl(10)
                cl = bl
                bl = tl

                val fr: Int
                val kr: Int
                when (j / 16) {
                    0 -> {
                        fr = br xor (cr or dr.inv())
                        kr = 0x50A28BE6
                    }
                    1 -> {
                        fr = (br and dr) or (cr and dr.inv())
                        kr = 0x5C4DD124
                    }
                    2 -> {
                        fr = (br or cr.inv()) xor dr
                        kr = 0x6D703EF3
                    }
                    3 -> {
                        fr = (br and cr) or (br.inv() and dr)
                        kr = 0x7A6D76E9
                    }
                    else -> {
                        fr = br xor cr xor dr
                        kr = 0x00000000
                    }
                }
                var tr = ar + fr + x[rr[j]] + kr
                tr = tr.rotl(ss[j]) + er
                ar = er
                er = dr
                dr = cr.rotl(10)
                cr = br
                br = tr
            }

            val t = h1 + cl + dr
            h1 = h2 + dl + er
            h2 = h3 + el + ar
            h3 = h4 + al + br
            h4 = h0 + bl + cr
            h0 = t
        }

        val out = ByteArray(20)
        for ((idx, h) in intArrayOf(h0, h1, h2, h3, h4).withIndex()) {
            out[idx * 4] = h.toByte()
            out[idx * 4 + 1] = (h shr 8).toByte()
            out[idx * 4 + 2] = (h shr 16).toByte()
            out[idx * 4 + 3] = (h shr 24).toByte()
        }
        return out
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
        val checksum = bech32CreateChecksum(hrp, values)
        val combined = values + checksum
        val sb = StringBuilder(hrp)
        sb.append('1')
        for (v in combined) {
            sb.append(BECH32_CHARSET[v])
        }
        return sb.toString()
    }

    private fun bech32CreateChecksum(hrp: String, data: List<Int>): List<Int> {
        val values = bech32HrpExpand(hrp) + data + listOf(0, 0, 0, 0, 0, 0)
        val polymod = bech32Polymod(values) xor 1
        return (0 until 6).map { (polymod shr (5 * (5 - it))) and 31 }
    }

    private fun bech32HrpExpand(hrp: String): List<Int> {
        val result = mutableListOf<Int>()
        for (c in hrp) {
            result.add(c.code shr 5)
        }
        result.add(0)
        for (c in hrp) {
            result.add(c.code and 31)
        }
        return result
    }

    private fun bech32Polymod(values: List<Int>): Int {
        val gen = intArrayOf(0x3b6a57b2, 0x26508e6d, 0x1ea119fa, 0x3d4233dd, 0x2a1462b3)
        var chk = 1
        for (v in values) {
            val top = chk shr 25
            chk = ((chk and 0x1ffffff) shl 5) xor v
            for (i in gen.indices) {
                if ((top shr i) and 1 != 0) {
                    chk = chk xor gen[i]
                }
            }
        }
        return chk
    }
}

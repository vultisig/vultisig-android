package com.vultisig.wallet.data.blockchain.cosmos.staking

/**
 * Test-only BIP-173 bech32 encoder used by [ValidatorBech32PreflightTests] and
 * [CosmosStakingSignDataResolverTests] to assemble valid `terravaloper1…` strings for assertion
 * inputs. Mirror inverse of the production [Bech32Decoder] in `ValidatorBech32Preflight.kt`.
 *
 * NOT used in production code — production validation goes through the decoder. Kept here so test
 * fixtures can produce checksum-valid inputs without depending on the Trust Wallet Core JNI
 * library.
 */
internal object Bech32TestEncoder {

    private const val CHARSET = "qpzry9x8gf2tvdw0s3jn54khce6mua7l"
    private val GENERATOR = intArrayOf(0x3b6a57b2, 0x26508e6d, 0x1ea119fa, 0x3d4233dd, 0x2a1462b3)

    fun encode(hrp: String, payload: ByteArray): String {
        val data5Bit = convertBits(payload.map { it.toInt() and 0xFF }, 8, 5, pad = true)
        val checksum = createChecksum(hrp, data5Bit)
        val combined = data5Bit + checksum
        val tail = combined.map { CHARSET[it] }.joinToString("")
        return hrp + "1" + tail
    }

    private fun convertBits(data: List<Int>, fromBits: Int, toBits: Int, pad: Boolean): List<Int> {
        var acc = 0
        var bits = 0
        val result = mutableListOf<Int>()
        val maxv = (1 shl toBits) - 1
        for (value in data) {
            acc = (acc shl fromBits) or value
            bits += fromBits
            while (bits >= toBits) {
                bits -= toBits
                result.add((acc shr bits) and maxv)
            }
        }
        if (pad && bits > 0) {
            result.add((acc shl (toBits - bits)) and maxv)
        }
        return result
    }

    private fun createChecksum(hrp: String, data: List<Int>): List<Int> {
        val values = hrpExpand(hrp) + data + List(6) { 0 }
        val mod = polymod(values) xor 1
        return (0 until 6).map { (mod shr (5 * (5 - it))) and 0x1f }
    }

    private fun hrpExpand(hrp: String): List<Int> {
        val bytes = hrp.toByteArray(Charsets.UTF_8).map { it.toInt() and 0xFF }
        return bytes.map { it ushr 5 } + 0 + bytes.map { it and 0x1f }
    }

    private fun polymod(values: List<Int>): Int {
        var chk = 1
        for (value in values) {
            val top = chk ushr 25
            chk = ((chk and 0x1ffffff) shl 5) xor value
            for (index in 0 until 5) {
                if (((top ushr index) and 1) == 1) chk = chk xor GENERATOR[index]
            }
        }
        return chk
    }
}

package com.vultisig.wallet.data.chains.helpers

import java.math.BigInteger

/**
 * The two SCALE encodings every Substrate signing payload is assembled from: `Compact<T>` for the
 * nonce, tip and transfer value, and the little-endian `u32` for the spec and transaction versions.
 * Shared by the hand-built Bittensor extrinsic and the dApp `signPayload` route, which must produce
 * bytes identical to the extension's `@polkadot/util` encoders for a ceremony to converge.
 */
internal object SubstrateScale {

    private val singleByteLimit = BigInteger.valueOf(1L shl 6)
    private val twoByteLimit = BigInteger.valueOf(1L shl 14)
    private val fourByteLimit = BigInteger.valueOf(1L shl 30)

    /**
     * `Compact<T>`: mode `0b00` for values below 2^6, `0b01` (two bytes) below 2^14, `0b10` (four
     * bytes) below 2^30, else `0b11` with the minimal little-endian magnitude behind a length byte.
     */
    fun compact(value: BigInteger): ByteArray {
        require(value.signum() >= 0) {
            "SCALE compact encoding requires non-negative value, got $value"
        }
        return when {
            value < singleByteLimit -> byteArrayOf((value.toInt() shl 2).toByte())
            value < twoByteLimit -> {
                val v = (value.toLong() shl 2) or 1L
                byteArrayOf((v and 0xFF).toByte(), ((v shr 8) and 0xFF).toByte())
            }
            value < fourByteLimit -> {
                val v = (value.toLong() shl 2) or 2L
                byteArrayOf(
                    (v and 0xFF).toByte(),
                    ((v shr 8) and 0xFF).toByte(),
                    ((v shr 16) and 0xFF).toByte(),
                    ((v shr 24) and 0xFF).toByte(),
                )
            }
            else -> {
                // BigInteger is big-endian two's complement, so a magnitude with its top bit set
                // carries one leading sign byte that is not part of the SCALE magnitude.
                val bytes =
                    value.toByteArray().let { b ->
                        val trimmed =
                            if (b[0] == 0.toByte() && b.size > 1) b.drop(1).toByteArray() else b
                        trimmed.reversedArray()
                    }
                val prefix = ((bytes.size - 4) shl 2) or 3
                byteArrayOf(prefix.toByte()) + bytes
            }
        }
    }

    /** Little-endian `u32`; the caller has already bounded [value] to `0..0xFFFFFFFF`. */
    fun u32LE(value: Long): ByteArray =
        byteArrayOf(
            (value and 0xFF).toByte(),
            ((value shr 8) and 0xFF).toByte(),
            ((value shr 16) and 0xFF).toByte(),
            ((value shr 24) and 0xFF).toByte(),
        )
}

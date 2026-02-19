@file:OptIn(ExperimentalStdlibApi::class)

package com.vultisig.wallet.data.crypto

import java.math.BigInteger
import java.security.MessageDigest

/**
 * Ed25519 scalar processing utilities for KeyImport.
 * Applies SHA-512 hashing, clamping, and mod-L reduction to raw Ed25519 seeds
 * before they are passed to Schnorr TSS keygen. Matches iOS implementation.
 */
internal object Ed25519ScalarUtil {

    // ed25519 group order L
    private val L = BigInteger(
        "7237005577332262213973186563042994240857116359379907606001950938285454250989"
    )

    /**
     * Converts a raw 32-byte Ed25519 seed into a uniform scalar suitable for
     * Schnorr TSS keygen. Steps: SHA-512 hash → take lower 32 bytes →
     * clamp per RFC 8032 → reduce mod L (group order).
     */
    fun clampThenUniformScalar(seed: ByteArray): ByteArray {
        require(seed.size == 32) { "Seed must be 32 bytes" }

        val hash = MessageDigest.getInstance("SHA-512").digest(seed)
        val scalar = hash.copyOf(32)

        // RFC 8032 clamping: clear lowest 3 bits, clear top 2 bits, set bit 254
        scalar[0] = (scalar[0].toInt() and 0xF8).toByte()
        scalar[31] = (scalar[31].toInt() and 0x3F).toByte()
        scalar[31] = (scalar[31].toInt() or 0x40).toByte()

        // Interpret as little-endian unsigned BigInteger
        val reversed = scalar.reversedArray()
        val value = BigInteger(1, reversed)

        // Reduce mod L
        val reduced = value.mod(L)

        // Convert back to 32-byte little-endian
        val reducedBytes = reduced.toByteArray()
        val result = ByteArray(32)
        // BigInteger.toByteArray() is big-endian, possibly with leading sign byte
        val significantBytes = if (reducedBytes[0] == 0.toByte() && reducedBytes.size > 32) {
            reducedBytes.drop(1).toByteArray()
        } else {
            reducedBytes
        }
        // Copy in reverse (big-endian → little-endian)
        for (i in significantBytes.indices) {
            result[i] = significantBytes[significantBytes.size - 1 - i]
        }

        return result
    }
}

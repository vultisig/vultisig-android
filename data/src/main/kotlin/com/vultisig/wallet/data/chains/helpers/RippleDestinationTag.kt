package com.vultisig.wallet.data.chains.helpers

import java.math.BigInteger
import java.security.MessageDigest

/**
 * XRPL destination-tag + X-address (XLS-5d) helpers.
 *
 * The tag is carried in the first-class `RippleSpecific.destination_tag` proto field, independent
 * of the free-text memo. [parseCanonicalDestinationTag] is the single definition of "canonical": a
 * uint32 in 1..4294967295 (proto3 can't distinguish a present 0 from "no tag").
 *
 * WalletCore ships no X-address codec on its JNI surface, so the codec is hand-rolled here:
 * XRPL-alphabet base58 + sha256d checksum, matching the canonical `ripple-address-codec` vectors.
 */
object RippleDestinationTag {

    private const val MAX_U32 = 4_294_967_295L

    /**
     * Parses a canonical XRPL destination tag: a base-10 uint32 in 1..4294967295, no leading zeros,
     * no sign, no whitespace. Returns null for anything else (including `"0"` and empty) — proto3's
     * scalar uint32 can't distinguish a present 0 from "no tag", so 0 is treated as "no tag".
     */
    fun parseCanonicalDestinationTag(text: String?): UInt? {
        val s = text?.trim() ?: return null
        if (s.isEmpty() || !s.all { it in '0'..'9' }) return null
        if (s.length > 1 && s[0] == '0') return null // no leading zeros
        val value = s.toLongOrNull() ?: return null
        if (value < 1 || value > MAX_U32) return null
        return value.toUInt()
    }

    /** True when [text] is a non-empty string that is NOT a canonical destination tag. */
    fun isNonCanonicalTag(text: String?): Boolean =
        !text.isNullOrBlank() && parseCanonicalDestinationTag(text) == null

    private const val XRPL_ALPHABET = "rpshnaf39wBUDNEGHJKLM4PQRST7VWXYZ2bcdeCg65jkm8oFqi1tuvAxyz"

    // XLS-5d payload prefixes.
    private val MAINNET_PREFIX = byteArrayOf(0x05, 0x44)

    data class XAddress(val classicAddress: String, val tag: UInt?)

    /**
     * Decodes a mainnet XRPL X-address (`X...`) into its classic `r...` address and embedded
     * destination tag. Returns null for anything that is not a valid mainnet X-address (bad
     * checksum, testnet, 64-bit tag, wrong length). A tag of 0 decodes to [XAddress.tag] == null.
     */
    fun decodeXAddress(input: String): XAddress? {
        val s = input.trim()
        if (!s.startsWith("X")) return null

        // 2 prefix + 20 accountID + 1 flag + 8 tag + 4 checksum
        val decoded = base58Decode(s)?.takeIf { it.size == 35 } ?: return null
        val payload = decoded.copyOfRange(0, 31)
        val checksum = decoded.copyOfRange(31, 35)
        if (!sha256(sha256(payload)).copyOfRange(0, 4).contentEquals(checksum)) return null

        if (!payload.copyOfRange(0, 2).contentEquals(MAINNET_PREFIX)) return null // testnet/other
        val accountId = payload.copyOfRange(2, 22)

        val flag = payload[22].toInt() and 0xff
        val tagBytes = payload.copyOfRange(23, 31)
        // High 4 bytes must be zero for a 32-bit tag; flag 2 (64-bit) is not supported.
        if (flag == 2 || tagBytes.copyOfRange(4, 8).any { it.toInt() != 0 }) return null

        val tagValue =
            (0 until 4).fold(0L) { acc, i -> acc or ((tagBytes[i].toLong() and 0xff) shl (8 * i)) }
        val tag =
            when (flag) {
                0 -> if (tagValue == 0L) null else return null // flag 0 must carry tag 0
                // A flag-1 X-address carrying tag 0 is rejected outright rather than normalized to
                // untagged: the sender explicitly encoded a tag, and proto3 can't carry a present
                // 0,
                // so signing it would silently send untagged behind a tag the user chose.
                1 -> if (tagValue in 1..MAX_U32) tagValue.toUInt() else return null
                else -> return null
            }

        // Classic address: base58check over [0x00 accountID-type prefix][accountID].
        val classic = base58EncodeCheck(byteArrayOf(0x00) + accountId)
        return XAddress(classicAddress = classic, tag = tag)
    }

    private fun base58Decode(input: String): ByteArray? {
        var num = BigInteger.ZERO
        val base = BigInteger.valueOf(58)
        for (c in input) {
            val digit = XRPL_ALPHABET.indexOf(c)
            if (digit < 0) return null
            num = num.multiply(base).add(BigInteger.valueOf(digit.toLong()))
        }
        var bytes = num.toByteArray()
        if (bytes.size > 1 && bytes[0].toInt() == 0) bytes = bytes.copyOfRange(1, bytes.size)
        val leadingZeros = input.takeWhile { it == XRPL_ALPHABET[0] }.length
        return ByteArray(leadingZeros) + bytes
    }

    private fun base58EncodeCheck(payload: ByteArray): String {
        val checksum = sha256(sha256(payload)).copyOfRange(0, 4)
        val full = payload + checksum
        val leadingZeros = full.takeWhile { it.toInt() == 0 }.size
        var num = BigInteger(1, full)
        val base = BigInteger.valueOf(58)
        val sb = StringBuilder()
        while (num > BigInteger.ZERO) {
            val (q, r) = num.divideAndRemainder(base)
            sb.append(XRPL_ALPHABET[r.toInt()])
            num = q
        }
        repeat(leadingZeros) { sb.append(XRPL_ALPHABET[0]) }
        return sb.reverse().toString()
    }

    private fun sha256(data: ByteArray): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(data)
}

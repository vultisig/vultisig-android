@file:OptIn(ExperimentalStdlibApi::class)

package com.vultisig.wallet.data.crypto.ton

import java.math.BigInteger

/**
 * Parse failure raised by the minimal TON BOC reader. Thrown at decoder boundaries and caught
 * there, so a malformed or truncated body falls back to "unknown" rather than crashing the keysign
 * UI.
 */
internal class TonCellException(message: String) : Exception(message)

/**
 * Immutable cell parsed from a BOC. Only the data needed for body decode is retained: the bit
 * payload and the ordered refs.
 */
internal class TonCell(val bits: TonBitString, val refs: List<TonCell>) {
    fun beginParse(): TonSlice = TonSlice(bits, refs)
}

/**
 * Bit-addressable byte buffer. Length is tracked in bits because TON cells carry a non-byte-aligned
 * bit count.
 */
internal class TonBitString(val bytes: ByteArray, val length: Int) {
    fun bit(index: Int): Boolean {
        if (index < 0 || index >= length) return false
        val byte = bytes[index / 8].toInt()
        val mask = 1 shl (7 - index % 8)
        return (byte and mask) != 0
    }
}

/**
 * Cursor over a [TonBitString] and its refs. Mirrors `@ton/core`'s `Slice` just enough to decode
 * the message-body shapes Vultisig surfaces. Addresses are returned in raw `workchain:hex` form —
 * the UI layer applies WalletCore's user-friendly bounceable formatting, keeping this reader
 * JNI-free and unit testable on the JVM.
 */
internal class TonSlice(private val bits: TonBitString, refs: List<TonCell>) {
    private val refs = ArrayDeque(refs)
    private var bitOffset = 0

    val remainingBits: Int
        get() = bits.length - bitOffset

    val remainingRefs: Int
        get() = refs.size

    fun loadBit(): Boolean {
        if (remainingBits < 1) throw TonCellException("missing bits")
        val value = bits.bit(bitOffset)
        bitOffset++
        return value
    }

    /** Load [count] bits (≤ 64) as an unsigned integer, most-significant first. */
    fun loadUInt(count: Int): Long {
        if (count < 0 || count > 64) throw TonCellException("invalid number width")
        if (remainingBits < count) throw TonCellException("missing bits")
        var value = 0L
        repeat(count) {
            value = value shl 1
            if (bits.bit(bitOffset)) value = value or 1L
            bitOffset++
        }
        return value
    }

    /** Load [count] bits as an unsigned big integer (used for query ids and coins). */
    fun loadUIntBig(count: Int): BigInteger {
        if (count == 0) return BigInteger.ZERO
        return BigInteger(1, loadBytes(count))
    }

    /**
     * `var_uint$_ len:(## 4) value:(uint (len * 8))` — TON's variable-length "Coins"/Grams
     * encoding.
     */
    fun loadCoins(): BigInteger {
        val length = loadUInt(4).toInt()
        if (length == 0) return BigInteger.ZERO
        val bitCount = length * 8
        if (bitCount > 256) throw TonCellException("invalid coins length")
        return loadUIntBig(bitCount)
    }

    fun loadRef(): TonCell {
        if (refs.isEmpty()) throw TonCellException("missing ref")
        return refs.removeFirst()
    }

    /** Skip a `Maybe ^Cell` field, returning the ref when the discriminator is set. */
    fun loadMaybeRef(): TonCell? = if (loadBit()) loadRef() else null

    /**
     * Read a TLB `MsgAddressInt`, returning the raw `workchain:hex` form. Throws on `addr_none` —
     * callers wanting optional behaviour use [loadMaybeAddress].
     */
    fun loadAddress(): String =
        loadAddressInternal(allowNone = false) ?: throw TonCellException("invalid address")

    /** Read an optional `MsgAddress`. Returns `null` for `addr_none$00`. */
    fun loadMaybeAddress(): String? = loadAddressInternal(allowNone = true)

    private fun loadAddressInternal(allowNone: Boolean): String? {
        if (remainingBits < 2) throw TonCellException("invalid address")
        return when (loadUInt(2).toInt()) {
            0b00 -> {
                if (allowNone) null else throw TonCellException("invalid address")
            }

            0b10 -> {
                // anycast:(Maybe Anycast) — not exercised by jetton/nft bodies;
                // reject rather than guess at the rewrite-prefix encoding.
                if (loadBit()) throw TonCellException("invalid address")
                // workchain is a signed int8: reinterpret the high bit as sign.
                val workchain = loadUInt(8).toByte().toInt()
                val hash = loadBytes(256)
                "$workchain:${hash.toHexString()}"
            }

            else -> throw TonCellException("invalid address")
        }
    }

    /**
     * Load [count] bits into a big-endian byte array, right-aligned with leading zeros so the value
     * round-trips through [BigInteger].
     */
    private fun loadBytes(count: Int): ByteArray {
        if (remainingBits < count) throw TonCellException("missing bits")
        val out = ByteArray((count + 7) / 8)
        val leadingZero = (8 - count % 8) % 8
        for (pos in 0 until count) {
            if (bits.bit(bitOffset)) {
                val absolute = pos + leadingZero
                out[absolute / 8] =
                    (out[absolute / 8].toInt() or (1 shl (7 - absolute % 8))).toByte()
            }
            bitOffset++
        }
        return out
    }
}

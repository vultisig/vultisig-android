package com.vultisig.wallet.data.crypto.ton

import java.math.BigInteger

/**
 * Builds a single ordinary TON cell — the write-side twin of [TonSlice]. Mirrors `@ton/core`'s
 * `Builder` just enough to encode the message bodies this app produces itself (a liquid-staking
 * deposit, a jetton burn): unsigned integers, `VarUInteger 16` coins, `MsgAddressInt`, and refs.
 *
 * Addresses are taken in raw `workchain:hex` form so the builder stays JNI-free and unit testable
 * on the JVM; the caller converts a user-friendly address with [tonFriendlyToRaw]. Every store
 * throws [TonCellException] rather than silently truncating, because a body that does not fit is a
 * body the contract will misread.
 */
internal class TonCellBuilder {

    private val data = ByteArray(MAX_BITS / 8 + 1)
    private var bitLength = 0
    private val refs = ArrayList<TonCell>(MAX_REFS)

    fun storeBit(value: Boolean): TonCellBuilder {
        if (bitLength >= MAX_BITS) throw TonCellException("cell overflow")
        if (value) {
            data[bitLength / 8] =
                (data[bitLength / 8].toInt() or (1 shl (7 - bitLength % 8))).toByte()
        }
        bitLength++
        return this
    }

    /** Store [value] in [bitCount] bits (≤ 64), most-significant first. */
    fun storeUInt(value: Long, bitCount: Int): TonCellBuilder {
        if (bitCount < 0 || bitCount > 64) throw TonCellException("invalid number width")
        if (value < 0 || (bitCount < 64 && value ushr bitCount != 0L)) {
            throw TonCellException("value does not fit in $bitCount bits")
        }
        for (i in bitCount - 1 downTo 0) storeBit((value ushr i) and 1L != 0L)
        return this
    }

    /** Store a non-negative [value] in [bitCount] bits, most-significant first. */
    fun storeUIntBig(value: BigInteger, bitCount: Int): TonCellBuilder {
        if (value.signum() < 0 || value.bitLength() > bitCount) {
            throw TonCellException("value does not fit in $bitCount bits")
        }
        for (i in bitCount - 1 downTo 0) storeBit(value.testBit(i))
        return this
    }

    /**
     * `var_uint$_ len:(## 4) value:(uint (len * 8))` — TON's `VarUInteger 16` "Coins" encoding: the
     * minimal byte length in a nibble, then the value in exactly that many bytes.
     */
    fun storeCoins(value: BigInteger): TonCellBuilder {
        if (value.signum() < 0) throw TonCellException("negative coins")
        val byteCount = (value.bitLength() + 7) / 8
        if (byteCount > 15) throw TonCellException("coins exceed VarUInteger 16")
        storeUInt(byteCount.toLong(), 4)
        return storeUIntBig(value, byteCount * 8)
    }

    /**
     * `addr_std$10 anycast:(Maybe Anycast) workchain_id:int8 address:bits256`, with no anycast —
     * the only form a wallet or contract address takes. [rawAddress] is `workchain:hex`, as
     * [TonSlice.loadAddress] returns it.
     */
    fun storeAddress(rawAddress: String): TonCellBuilder {
        val separator = rawAddress.indexOf(':')
        if (separator <= 0) throw TonCellException("invalid address")
        val workchain =
            rawAddress.substring(0, separator).toIntOrNull()?.takeIf { it in -128..127 }
                ?: throw TonCellException("invalid address")
        val hash = rawAddress.substring(separator + 1)
        if (hash.length != 64 || !hash.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }) {
            throw TonCellException("invalid address")
        }
        storeUInt(0b10, 2)
        storeBit(false)
        storeUInt((workchain and 0xff).toLong(), 8)
        return storeUIntBig(BigInteger(hash, 16), 256)
    }

    fun storeRef(cell: TonCell): TonCellBuilder {
        if (refs.size >= MAX_REFS) throw TonCellException("too many refs")
        refs.add(cell)
        return this
    }

    /** `Maybe ^Cell`: a set discriminator bit followed by the ref, or a clear bit alone. */
    fun storeMaybeRef(cell: TonCell?): TonCellBuilder {
        storeBit(cell != null)
        if (cell != null) storeRef(cell)
        return this
    }

    fun build(): TonCell =
        TonCell(TonBitString(data.copyOf((bitLength + 7) / 8), bitLength), refs.toList())

    private companion object {
        const val MAX_BITS = 1023
        const val MAX_REFS = 4
    }
}

/**
 * Converts a user-friendly TON address (`EQ…`/`UQ…`, base64 or base64url) to the raw
 * `workchain:hex` form the cell builder stores. A raw address is returned as written. Returns
 * `null` when [address] is neither, so a caller can fail closed rather than encode garbage.
 *
 * Like [TonAddressFlags], this trusts the length and shape and does not re-verify the CRC16:
 * addresses reach it from the vault's own account or from a curated constant, both of which have
 * already been validated by WalletCore.
 */
internal fun tonFriendlyToRaw(address: String): String? {
    val trimmed = address.trim()
    if (trimmed.contains(':')) {
        val separator = trimmed.indexOf(':')
        val workchain = trimmed.substring(0, separator).toIntOrNull() ?: return null
        val hash = trimmed.substring(separator + 1).lowercase()
        if (hash.length != 64 || !hash.all { it in '0'..'9' || it in 'a'..'f' }) return null
        return "$workchain:$hash"
    }
    if (trimmed.length != 48) return null
    val bytes =
        runCatching { java.util.Base64.getUrlDecoder().decode(trimmed) }.getOrNull()
            ?: runCatching { java.util.Base64.getDecoder().decode(trimmed) }.getOrNull()
            ?: return null
    if (bytes.size != 36) return null
    val workchain = bytes[1].toInt()
    val hash = bytes.copyOfRange(2, 34).joinToString("") { "%02x".format(it.toInt() and 0xff) }
    return "$workchain:$hash"
}

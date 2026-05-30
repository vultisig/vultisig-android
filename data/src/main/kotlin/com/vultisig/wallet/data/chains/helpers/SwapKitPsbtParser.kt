package com.vultisig.wallet.data.chains.helpers

import com.vultisig.wallet.data.utils.Numeric

/**
 * Shared BIP-174 PSBT framing primitives. Ported from iOS' `SwapKitPSBTParser`. Every SwapKit UTXO
 * signer consumes the same byte-level envelope — magic prefix, global key/value map, per-input map,
 * per-output map. Per-chain signers diverge only on the unsigned-tx body parser (which lives with
 * the chain's serialization rules in the per-chain signer) and on script-type classification.
 *
 * Pure bytes: no crypto and no vault context. The unsigned-tx body (global key `0x00`) is exposed
 * as raw bytes; the caller parses it because the serialization differs per chain (BIP-144 segwit
 * for BTC, legacy for DOGE/BCH/DASH, Sapling-v4 for ZEC).
 */
internal class SwapKitPsbtException(message: String) : Exception(message)

/**
 * Framing header — magic consumed, globals map parsed, unsigned-tx bytes extracted. The cursor is
 * left positioned at the first per-input map so the caller can stream the input/output maps once it
 * knows the counts from the unsigned-tx body.
 */
internal class PsbtFramingHeader(
    val cursor: PsbtCursor,
    val globals: Map<String, ByteArray>,
    val unsignedTxBytes: ByteArray,
)

internal object SwapKitPsbtParser {

    // BIP-174 magic: 4-byte ASCII 'psbt' (0x70 0x73 0x62 0x74) + a single 0xff separator.
    private val MAGIC = byteArrayOf(0x70, 0x73, 0x62, 0x74, 0xff.toByte())

    // PSBT_GLOBAL_UNSIGNED_TX key is the single byte 0x00.
    private const val GLOBAL_UNSIGNED_TX_KEY = "00"

    /**
     * Read the framing header + globals and extract the mandatory `PSBT_GLOBAL_UNSIGNED_TX` value.
     * The returned cursor is positioned at the first per-input map; the caller reads one map per
     * input and one per output (counts come from the unsigned-tx body it parses itself).
     */
    fun parseFramingHeader(psbtBytes: ByteArray): PsbtFramingHeader {
        if (psbtBytes.isEmpty()) throw SwapKitPsbtException("SwapKit PSBT payload is empty")
        val cursor = PsbtCursor(psbtBytes)
        cursor.expectMagic(MAGIC)
        val globals = cursor.readMap()
        val unsignedTx =
            globals[GLOBAL_UNSIGNED_TX_KEY]
                ?: throw SwapKitPsbtException(
                    "SwapKit PSBT is missing the PSBT_GLOBAL_UNSIGNED_TX global record"
                )
        return PsbtFramingHeader(cursor, globals, unsignedTx)
    }
}

/**
 * Cursor into a PSBT byte stream implementing the BIP-174 wire helpers. Mutable offset; all readers
 * throw [SwapKitPsbtException] on truncation/malformation so the per-chain signer wraps once.
 */
internal class PsbtCursor(private val data: ByteArray) {
    var offset: Int = 0
        private set

    val isAtEnd: Boolean
        get() = offset >= data.size

    fun expectMagic(magic: ByteArray) {
        if (data.size < magic.size)
            throw SwapKitPsbtException("SwapKit PSBT magic bytes are invalid")
        for (i in magic.indices) {
            if (data[i] != magic[i]) {
                throw SwapKitPsbtException("SwapKit PSBT magic bytes are invalid")
            }
        }
        offset = magic.size
    }

    /**
     * Read a BIP-174 key/value map up to its `0x00` terminator. Keys are hex-encoded so they can be
     * looked up by content. BIP-174 mandates unique keys within a map; duplicates are malformed and
     * rejected (a silent overwrite would let an adversarial upstream swap, e.g., a WITNESS_UTXO
     * amount under the parser's nose).
     */
    fun readMap(): Map<String, ByteArray> {
        val map = LinkedHashMap<String, ByteArray>()
        while (true) {
            val keyLen = readCompactSize()
            if (keyLen == 0L) return map // 0x00 terminator
            val key = readBytes(asLength(keyLen))
            val valLen = readCompactSize()
            val value = readBytes(asLength(valLen))
            val keyHex = Numeric.toHexStringNoPrefix(key)
            if (map.containsKey(keyHex)) {
                throw SwapKitPsbtException("SwapKit PSBT is malformed: duplicate key 0x$keyHex")
            }
            map[keyHex] = value
        }
    }

    fun readCompactSize(): Long =
        when (val head = readByte().toInt() and 0xFF) {
            0xff -> readUInt64LE()
            0xfe -> readUInt32LE()
            0xfd -> readUInt16LE().toLong()
            else -> head.toLong()
        }

    fun readByte(): Byte {
        if (offset >= data.size) throw SwapKitPsbtException("SwapKit PSBT is truncated")
        return data[offset++]
    }

    fun readBytes(count: Int): ByteArray {
        if (count < 0 || offset + count > data.size) {
            throw SwapKitPsbtException("SwapKit PSBT is truncated")
        }
        val slice = data.copyOfRange(offset, offset + count)
        offset += count
        return slice
    }

    /**
     * Narrow a compactSize/length to an [Int] for [readBytes], with a clear error instead of the
     * silent wrap-to-negative `toInt()` would produce on a value ≥ 2^31 (which would then surface
     * as a misleading "truncated").
     */
    fun asLength(value: Long): Int {
        if (value > Int.MAX_VALUE) {
            throw SwapKitPsbtException(
                "SwapKit PSBT field length $value exceeds the supported limit"
            )
        }
        return value.toInt()
    }

    // Little-endian readers assembled byte-by-byte (no alignment assumptions).
    fun readUInt16LE(): Int {
        val b0 = readByte().toInt() and 0xFF
        val b1 = readByte().toInt() and 0xFF
        return b0 or (b1 shl 8)
    }

    fun readUInt32LE(): Long {
        val b0 = readByte().toLong() and 0xFF
        val b1 = readByte().toLong() and 0xFF
        val b2 = readByte().toLong() and 0xFF
        val b3 = readByte().toLong() and 0xFF
        return b0 or (b1 shl 8) or (b2 shl 16) or (b3 shl 24)
    }

    fun readUInt64LE(): Long {
        var result = 0L
        for (i in 0 until 8) {
            result = result or ((readByte().toLong() and 0xFF) shl (i * 8))
        }
        return result
    }
}

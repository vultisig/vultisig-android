@file:OptIn(ExperimentalStdlibApi::class)

package com.vultisig.wallet.data.crypto.ton

import java.util.Base64

/**
 * Minimal TON BOC (Bag Of Cells) parser. Converts a base64 (or hex) payload to the root cell of its
 * tree. Only single-root, ordinary-cell BOCs are accepted; indexed roots, exotic cells, and
 * trailing junk are rejected so a hostile payload cannot hide bytes outside the Verify summary's
 * view.
 */
internal object TonBocParser {

    private val HEX_BOC_MAGIC_PREFIXES = listOf("b5ee9c72", "68ff65f3", "acc3a728")

    // CRC-32C (Castagnoli) reflected polynomial.
    private val CRC32C_POLYNOMIAL = 0x82F63B78.toInt()

    /**
     * Convert a hex-encoded BOC to base64 when it carries a recognised magic prefix; otherwise
     * return the trimmed input unchanged. Returns `null` for blank input.
     */
    fun payloadToBase64(payload: String?): String? {
        val trimmed = payload?.trim().orEmpty()
        if (trimmed.isEmpty()) return null
        if (!isHexBoc(trimmed)) return trimmed
        return runCatching { Base64.getEncoder().encodeToString(trimmed.hexToByteArray()) }
            .getOrNull()
    }

    fun parse(base64: String): TonCell {
        val bytes =
            runCatching { Base64.getDecoder().decode(base64) }.getOrNull()
                ?: throw TonCellException("invalid base64 BOC")
        return parse(bytes)
    }

    private fun parse(bytes: ByteArray): TonCell {
        if (bytes.size < 6) throw TonCellException("truncated BOC")
        // Only the canonical b5ee9c72 magic carries a parseable layout — the
        // alternate magics surface only in payloadToBase64 hex detection.
        if (
            u(bytes, 0) != 0xb5 || u(bytes, 1) != 0xee || u(bytes, 2) != 0x9c || u(bytes, 3) != 0x72
        ) {
            throw TonCellException("invalid BOC magic")
        }

        var cursor = 4
        val flags = u(bytes, cursor)
        cursor++
        val hasIdx = (flags and 0x80) != 0
        val hasCrc32c = (flags and 0x40) != 0
        // bit 0x20 (hasCacheBits) and bits 4..3 are metadata we don't read.
        val sizeBytes = flags and 0x07
        if (sizeBytes < 1 || sizeBytes > 4) throw TonCellException("truncated BOC")

        if (cursor >= bytes.size) throw TonCellException("truncated BOC")
        val offBytes = u(bytes, cursor)
        cursor++
        if (offBytes < 1 || offBytes > 8) throw TonCellException("truncated BOC")

        if (cursor + sizeBytes * 3 + offBytes > bytes.size) throw TonCellException("truncated BOC")
        val cellsCount = readBigEndian(bytes, cursor, sizeBytes)
        cursor += sizeBytes
        val rootsCount = readBigEndian(bytes, cursor, sizeBytes)
        cursor += sizeBytes
        val absentCount = readBigEndian(bytes, cursor, sizeBytes)
        cursor += sizeBytes
        val totCellsSizeRaw = readBigEndian(bytes, cursor, offBytes)
        cursor += offBytes
        // offBytes can be up to 8, so a hostile header could declare a length
        // beyond the buffer (or negative as a signed Long). Bound it here, the
        // way iOS's Int(exactly:) does.
        if (totCellsSizeRaw < 0 || totCellsSizeRaw > bytes.size)
            throw TonCellException("truncated BOC")
        val totCellsSize = totCellsSizeRaw.toInt()

        // Single-root BOCs only — this parser returns one cell, so accepting
        // more roots would silently drop siblings.
        if (rootsCount != 1L || absentCount != 0L || cellsCount < rootsCount) {
            throw TonCellException("unsupported BOC layout")
        }

        // root_list
        val rootListSize = (rootsCount * sizeBytes).toInt()
        if (cursor + rootListSize > bytes.size) throw TonCellException("truncated BOC")
        val rootIndex = readBigEndian(bytes, cursor, sizeBytes)
        cursor += rootListSize
        if (rootIndex >= cellsCount) throw TonCellException("truncated BOC")

        if (hasIdx) {
            val indexSize = cellsCount * offBytes
            if (cursor + indexSize > bytes.size) throw TonCellException("truncated BOC")
            cursor += indexSize.toInt()
        }

        // Strict envelope: the buffer ends exactly at the cells data plus the
        // optional 4-byte CRC trailer. Trailing junk would let an attacker hide
        // bytes outside the parser's view.
        val trailerSize = if (hasCrc32c) 4 else 0
        if (cursor.toLong() + totCellsSize + trailerSize != bytes.size.toLong()) {
            throw TonCellException("truncated BOC")
        }

        // Each serialized cell needs at least its 2-byte header, so cellsCount
        // is bounded by totCellsSize / 2 — guards against an extreme declared
        // count exhausting memory before the structural failure surfaces.
        if (cellsCount > (totCellsSize / 2).toLong()) throw TonCellException("invalid cell header")
        val count = cellsCount.toInt()

        // Phase 1: parse each cell into raw form (bits + ref indices).
        val cellBits = arrayOfNulls<TonBitString>(count)
        val cellRefs = arrayOfNulls<IntArray>(count)
        val cellsEnd = cursor + totCellsSize
        var cellsCursor = cursor
        for (i in 0 until count) {
            if (cellsCursor + 2 > cellsEnd) throw TonCellException("invalid cell header")
            val d1 = u(bytes, cellsCursor)
            cellsCursor++
            val d2 = u(bytes, cellsCursor)
            cellsCursor++
            val refsCount = d1 and 0x07
            val isExotic = (d1 and 0x08) != 0
            val hasHashes = (d1 and 0x10) != 0
            if (isExotic) throw TonCellException("unsupported cell type")

            val dataBytes = (d2 + 1) / 2
            val isAligned = (d2 and 1) == 0

            if (hasHashes) {
                // Skip pruned-branch hashes (32 bytes) + depths (2 bytes) per
                // level. Ordinary cells have level 0 → one hash + one depth.
                val levelMask = (d1 shr 5) and 0x07
                val levels = Integer.bitCount(levelMask) + 1
                val skip = levels * (32 + 2)
                if (cellsCursor + skip > cellsEnd) throw TonCellException("invalid cell header")
                cellsCursor += skip
            }

            if (cellsCursor + dataBytes > cellsEnd) throw TonCellException("invalid cell header")
            val payload = bytes.copyOfRange(cellsCursor, cellsCursor + dataBytes)
            cellsCursor += dataBytes

            val bitLength =
                if (isAligned) {
                    dataBytes * 8
                } else {
                    // Non-aligned cells append a `1` marker then `0`s to the
                    // byte boundary; strip everything at or below the lowest set
                    // bit to recover the real bit length.
                    val last = if (payload.isNotEmpty()) u(payload, payload.size - 1) else 0
                    if (last == 0) throw TonCellException("invalid cell header")
                    dataBytes * 8 - (Integer.numberOfTrailingZeros(last) + 1)
                }
            cellBits[i] = TonBitString(payload, bitLength)

            if (cellsCursor + refsCount * sizeBytes > cellsEnd)
                throw TonCellException("invalid cell header")
            val refIndices = IntArray(refsCount)
            for (r in 0 until refsCount) {
                val refIndex = readBigEndian(bytes, cellsCursor, sizeBytes)
                cellsCursor += sizeBytes
                if (refIndex >= count) throw TonCellException("invalid cell header")
                refIndices[r] = refIndex.toInt()
            }
            cellRefs[i] = refIndices
        }

        // Cells must consume the declared region exactly.
        if (cellsCursor != cellsEnd) throw TonCellException("invalid cell header")

        // Phase 2: tie refs together. BOCs serialize children before parents, so
        // iterating from the last cell upward guarantees referenced cells exist.
        val resolved = arrayOfNulls<TonCell>(count)
        for (i in count - 1 downTo 0) {
            val children =
                cellRefs[i]!!.map { childIndex ->
                    resolved[childIndex] ?: throw TonCellException("invalid cell header")
                }
            resolved[i] = TonCell(cellBits[i]!!, children)
        }

        if (hasCrc32c) {
            // CRC-32C (Castagnoli) over every byte before the little-endian
            // 4-byte trailer.
            val stored =
                u(bytes, cellsEnd) or
                    (u(bytes, cellsEnd + 1) shl 8) or
                    (u(bytes, cellsEnd + 2) shl 16) or
                    (u(bytes, cellsEnd + 3) shl 24)
            if (stored != crc32c(bytes, cellsEnd)) throw TonCellException("invalid cell header")
        }

        return resolved[rootIndex.toInt()] ?: throw TonCellException("truncated BOC")
    }

    private fun isHexBoc(payload: String): Boolean {
        if (payload.length % 2 != 0) return false
        if (!payload.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }) return false
        val lower = payload.lowercase()
        return HEX_BOC_MAGIC_PREFIXES.any { lower.startsWith(it) }
    }

    /**
     * CRC-32C (Castagnoli) over the first [length] bytes, init/xor-out `0xFFFFFFFF`. Hand-rolled
     * rather than `java.util.zip.CRC32C`, which is only available from API 34 (this module's min is
     * 26).
     */
    private fun crc32c(bytes: ByteArray, length: Int): Int {
        var crc = -1 // 0xFFFFFFFF
        for (i in 0 until length) {
            crc = crc xor (bytes[i].toInt() and 0xFF)
            repeat(8) { crc = (crc ushr 1) xor (if (crc and 1 != 0) CRC32C_POLYNOMIAL else 0) }
        }
        return crc.inv()
    }

    private fun readBigEndian(bytes: ByteArray, offset: Int, length: Int): Long {
        var result = 0L
        for (i in 0 until length) {
            result = (result shl 8) or (bytes[offset + i].toLong() and 0xFF)
        }
        return result
    }

    private fun u(bytes: ByteArray, index: Int): Int = bytes[index].toInt() and 0xFF
}

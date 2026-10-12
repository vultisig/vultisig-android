package com.vultisig.wallet.data.crypto.ton

import java.util.Base64

/**
 * Serializes a cell tree into a single-root TON BOC, laid out exactly as `@ton/core`'s default
 * `Cell.toBoc()` emits it: no index, a CRC-32C trailer, minimal size fields, and cells in
 * depth-first order with every ref pointing at a later index. That is the layout [TonBocParser]
 * accepts and the one wallet-core re-parses when a transfer carries the result as its
 * `customPayload`, so what this app builds decodes back through its own reader.
 *
 * Cells are not deduplicated: the bodies built here are small trees with no shared subtrees, and a
 * duplicate would only cost bytes, never correctness.
 */
internal object TonBocSerializer {

    private val MAGIC = byteArrayOf(0xb5.toByte(), 0xee.toByte(), 0x9c.toByte(), 0x72.toByte())

    fun toBase64(root: TonCell, withCrc32c: Boolean = true): String =
        Base64.getEncoder().encodeToString(serialize(root, withCrc32c))

    fun serialize(root: TonCell, withCrc32c: Boolean = true): ByteArray {
        val cells = ArrayList<TonCell>()
        collect(root, cells)
        val count = cells.size
        val sizeBytes = bytesNeeded(count.toLong())

        val serialized = cells.map { serializeCell(it, cells, sizeBytes) }
        val totalCellsSize = serialized.sumOf { it.size }
        val offBytes = bytesNeeded(totalCellsSize.toLong())

        val header = java.io.ByteArrayOutputStream()
        header.write(MAGIC)
        header.write((if (withCrc32c) 0x40 else 0x00) or sizeBytes)
        header.write(offBytes)
        header.writeBigEndian(count.toLong(), sizeBytes)
        header.writeBigEndian(1L, sizeBytes) // roots
        header.writeBigEndian(0L, sizeBytes) // absent
        header.writeBigEndian(totalCellsSize.toLong(), offBytes)
        header.writeBigEndian(0L, sizeBytes) // root index
        serialized.forEach(header::write)

        val body = header.toByteArray()
        if (!withCrc32c) return body

        val crc = TonBocParser.crc32c(body, body.size)
        return body +
            byteArrayOf(
                (crc and 0xff).toByte(),
                ((crc ushr 8) and 0xff).toByte(),
                ((crc ushr 16) and 0xff).toByte(),
                ((crc ushr 24) and 0xff).toByte(),
            )
    }

    /** Depth-first pre-order, so a parent always precedes (and indexes forward to) its refs. */
    private fun collect(cell: TonCell, into: MutableList<TonCell>) {
        into.add(cell)
        cell.refs.forEach { collect(it, into) }
    }

    private fun serializeCell(cell: TonCell, all: List<TonCell>, sizeBytes: Int): ByteArray {
        val bits = cell.bits
        val fullBytes = bits.length / 8
        val hasPartialByte = bits.length % 8 != 0
        val dataBytes = fullBytes + (if (hasPartialByte) 1 else 0)

        val out = java.io.ByteArrayOutputStream()
        // d1: refs count; level 0, ordinary, no stored hashes.
        out.write(cell.refs.size)
        // d2: floor(bits/8) + ceil(bits/8) — an odd value flags the completion tag below.
        out.write(fullBytes + dataBytes)

        val data = bits.bytes.copyOf(dataBytes)
        if (hasPartialByte) {
            // Completion tag: a `1` right after the last data bit, then zeros to the byte end.
            val tagIndex = bits.length % 8
            data[fullBytes] = (data[fullBytes].toInt() or (1 shl (7 - tagIndex))).toByte()
        }
        out.write(data)

        cell.refs.forEach { ref ->
            val index = all.indexOfFirst { it === ref }
            out.writeBigEndian(index.toLong(), sizeBytes)
        }
        return out.toByteArray()
    }

    private fun bytesNeeded(value: Long): Int {
        var bytes = 1
        var remaining = value ushr 8
        while (remaining != 0L) {
            bytes++
            remaining = remaining ushr 8
        }
        return bytes
    }

    private fun java.io.ByteArrayOutputStream.writeBigEndian(value: Long, byteCount: Int) {
        for (i in byteCount - 1 downTo 0) write(((value ushr (8 * i)) and 0xff).toInt())
    }
}

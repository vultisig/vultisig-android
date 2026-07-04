package com.vultisig.wallet.data.crypto

import com.vultisig.wallet.data.common.Utils
import kotlin.experimental.and

/**
 * Canonical CIP-20 transaction-metadata encoder for Cardano memos.
 *
 * A Cardano memo is CIP-20 metadata under the registered label 674:
 *
 *     { 674: { "msg": [ "<chunk1>", "<chunk2>", ... ] } }
 *
 * Each `msg` text chunk is at most 64 UTF-8 bytes, split on Unicode codepoint boundaries (never
 * mid-codepoint). WalletCore 4.7.0 consumes these CBOR bytes via
 * `Cardano.SigningInput.auxiliary_data`: it commits `blake2b-256(auxDataCbor)` into the tx body at
 * map key 7 (auxiliary_data_hash) and embeds the bytes as element [3] of the signed transaction
 * array.
 *
 * Cardano signing is MPC/TSS: every co-signing device (iOS / Android / Extension) builds the input
 * independently and the Blake2b sighash must match byte-for-byte, so this encoding MUST be
 * byte-identical across platforms. It mirrors the mainnet-verified iOS encoder `CardanoCIP20.swift`
 * and the SDK encoder `vultisig-sdk/.../cardano/buildCip20AuxData.ts`.
 */
object CardanoCIP20 {

    /** CIP-20 limits each metadata text chunk to 64 UTF-8 bytes. */
    private const val MAX_CHUNK_BYTES = 64

    /** The CIP-20 metadata label registered on cardano.org. */
    private const val METADATA_LABEL = 674

    /** Canonical CIP-20 auxiliary data plus its Blake2b-256 hash. */
    data class AuxData(val auxDataCbor: ByteArray, val auxDataHash: ByteArray)

    /**
     * Split a memo into chunks of at most 64 UTF-8 bytes, respecting UTF-8 codepoint boundaries.
     *
     * A multi-byte codepoint straddling the 64-byte boundary is moved entirely to the next chunk
     * rather than torn — a torn codepoint decodes to U+FFFD and corrupts the memo on-chain. UTF-8
     * continuation bytes have the top bits `10xxxxxx`; we walk back off them until the cut lands on
     * a leading byte. An empty memo yields a single empty-string chunk (matches the SDK, which
     * always emits at least one `msg` element).
     */
    fun memoToChunks(memo: String): List<String> {
        val bytes = memo.toByteArray(Charsets.UTF_8)
        if (bytes.isEmpty()) return listOf("")

        val chunks = mutableListOf<String>()
        var start = 0
        while (start < bytes.size) {
            var end = minOf(start + MAX_CHUNK_BYTES, bytes.size)

            // If we are not at the end and `end` lands on a continuation byte
            // (0b10xxxxxx), back up until the byte at `end` starts a codepoint.
            if (end < bytes.size) {
                while (end > start && (bytes[end] and 0xC0.toByte()) == 0x80.toByte()) {
                    end -= 1
                }
                // Defensive: a run of >64 continuation bytes is impossible for valid
                // UTF-8 (max 4 bytes/codepoint); fall back to the raw cut so we
                // always make forward progress.
                if (end == start) {
                    end = minOf(start + MAX_CHUNK_BYTES, bytes.size)
                }
            }

            // Cuts always land on a codepoint boundary, so decoding a slice of the
            // (always-valid) UTF-8 bytes never fails.
            chunks.add(String(bytes, start, end - start, Charsets.UTF_8))
            start = end
        }
        return chunks
    }

    /**
     * Encode the CIP-20 auxiliary data for a memo.
     *
     * Returns the canonical CBOR bytes for `{ 674: { "msg": [chunks] } }` (to be set on
     * `Cardano.SigningInput.auxiliary_data`) plus their `blake2b-256`, which WalletCore commits
     * into the tx body at map key 7.
     */
    fun buildAuxData(memo: String): AuxData {
        val chunks = memoToChunks(memo)
        val msgArray = cborArray(chunks.map { cborText(it) })
        val innerMap = cborMap(listOf(cborText("msg") to msgArray))
        val auxDataCbor = cborMap(listOf(cborUint(METADATA_LABEL) to innerMap))
        val auxDataHash = Utils.blake2bHash(auxDataCbor)
        return AuxData(auxDataCbor, auxDataHash)
    }

    // Minimal canonical CBOR primitives (RFC 8949 §3.1)

    /** CBOR text string (major type 3). */
    private fun cborText(string: String): ByteArray {
        val utf8 = string.toByteArray(Charsets.UTF_8)
        return cborHead(majorType = 3, value = utf8.size) + utf8
    }

    /** CBOR unsigned integer (major type 0). */
    private fun cborUint(value: Int): ByteArray = cborHead(majorType = 0, value = value)

    /** CBOR array header (major type 4) followed by its items. */
    private fun cborArray(items: List<ByteArray>): ByteArray {
        var out = cborHead(majorType = 4, value = items.size)
        for (item in items) out += item
        return out
    }

    /** CBOR map header (major type 5) followed by its key/value pairs. */
    private fun cborMap(entries: List<Pair<ByteArray, ByteArray>>): ByteArray {
        var out = cborHead(majorType = 5, value = entries.size)
        for ((key, value) in entries) {
            out += key
            out += value
        }
        return out
    }

    /** Encode the major-type + argument head in the smallest form (RFC 8949 §3.1). */
    private fun cborHead(majorType: Int, value: Int): ByteArray {
        val mt = (majorType shl 5)
        val v = value.toLong() and 0xFFFFFFFFL
        return when {
            v < 24 -> byteArrayOf((mt or v.toInt()).toByte())
            v < 0x100 -> byteArrayOf((mt or 24).toByte(), v.toByte())
            v < 0x1_0000 ->
                byteArrayOf(
                    (mt or 25).toByte(),
                    ((v shr 8) and 0xFF).toByte(),
                    (v and 0xFF).toByte(),
                )

            else ->
                byteArrayOf(
                    (mt or 26).toByte(),
                    ((v shr 24) and 0xFF).toByte(),
                    ((v shr 16) and 0xFF).toByte(),
                    ((v shr 8) and 0xFF).toByte(),
                    (v and 0xFF).toByte(),
                )
        }
    }
}

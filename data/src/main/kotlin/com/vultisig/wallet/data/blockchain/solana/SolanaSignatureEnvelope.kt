package com.vultisig.wallet.data.blockchain.solana

/**
 * The wire envelope of a Solana transaction: `[compact-u16 signature count][count × 64-byte
 * signature slot][message]`.
 *
 * Shared by the raw-signing path and by the checks that run before it, deliberately. The signing
 * path splices signer 0's signature into [firstSignatureOffset] and leaves every other slot as it
 * received it, so a check that a transaction is safe to sign that way has to read the same envelope
 * the splice writes into — a second, parallel parse could agree with itself and still describe
 * different bytes.
 *
 * @property message the pre-image ed25519 signs verbatim.
 * @property requiredSignatures the number of declared signature slots, which is the message
 *   header's `numRequiredSignatures`.
 */
data class SolanaSignatureEnvelope(
    val bytes: ByteArray,
    val firstSignatureOffset: Int,
    val requiredSignatures: Int,
    val message: ByteArray,
) {

    /**
     * Whether every declared signature slot is still an all-zero placeholder.
     *
     * What makes the splice at slot 0 safe: a slot already carrying bytes is somebody's signature
     * over some message, and this transaction would be broadcast carrying it.
     */
    val isUnsigned: Boolean
        get() =
            (firstSignatureOffset until
                    firstSignatureOffset + requiredSignatures * SIGNATURE_LENGTH)
                .all { bytes[it] == ZERO_BYTE }

    // ByteArray identity would make two structurally equal envelopes compare unequal.
    override fun equals(other: Any?): Boolean =
        this === other ||
            (other is SolanaSignatureEnvelope &&
                bytes.contentEquals(other.bytes) &&
                firstSignatureOffset == other.firstSignatureOffset &&
                requiredSignatures == other.requiredSignatures &&
                message.contentEquals(other.message))

    override fun hashCode(): Int =
        31 * (31 * (31 * bytes.contentHashCode() + firstSignatureOffset) + requiredSignatures) +
            message.contentHashCode()

    companion object {

        const val SIGNATURE_LENGTH = 64

        private const val ZERO_BYTE: Byte = 0

        /**
         * Splits [bytes] into its envelope.
         *
         * Reading the message out of the original bytes — rather than decoding into WalletCore's
         * representation and re-serializing it — keeps the pre-image hash independent of
         * WalletCore's encoder. That re-encode is not guaranteed to reproduce the original bytes
         * for a v0 message referencing an Address Lookup Table (the standard shape for
         * DEX/aggregator swaps), which would make co-signing devices compute mismatching hashes and
         * stall the ceremony.
         *
         * @throws IllegalStateException if [bytes] is not a well-formed envelope.
         */
        fun parse(bytes: ByteArray): SolanaSignatureEnvelope {
            val (signatureCount, firstSignatureOffset) = readCompactU16(bytes)
            check(signatureCount >= 1) { "Solana transaction declares no signatures" }

            val messageOffset = firstSignatureOffset + signatureCount * SIGNATURE_LENGTH
            check(messageOffset < bytes.size) {
                "Solana transaction too short for its $signatureCount declared signature(s)"
            }
            return SolanaSignatureEnvelope(
                bytes = bytes,
                firstSignatureOffset = firstSignatureOffset,
                requiredSignatures = signatureCount,
                message = bytes.copyOfRange(messageOffset, bytes.size),
            )
        }

        /**
         * Decodes the Solana compact-u16 (shortvec) at the start of [bytes] — up to three bytes, 7
         * payload bits each with the high bit signalling "more bytes follow" — and returns the
         * decoded value together with the offset just past it (where the signature slots begin).
         */
        private fun readCompactU16(bytes: ByteArray): Pair<Int, Int> {
            var value = 0
            var offset = 0
            var shift = 0
            while (offset < bytes.size) {
                val byte = bytes[offset].toInt() and 0xFF
                value = value or ((byte and 0x7F) shl shift)
                offset++
                if (byte and 0x80 == 0) return value to offset
                shift += 7
                if (shift > 14) error("Malformed compact-u16 in Solana transaction")
            }
            error("Truncated compact-u16 in Solana transaction")
        }
    }
}

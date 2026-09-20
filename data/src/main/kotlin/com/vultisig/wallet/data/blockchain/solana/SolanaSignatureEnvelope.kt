package com.vultisig.wallet.data.blockchain.solana

/**
 * The wire envelope of a Solana transaction: `[compact-u16 signature count][count × 64-byte
 * signature slot][message]`.
 *
 * Shared by the raw-signing path and by the checks that run before it, deliberately. The signing
 * path splices the vault's signature into the vault's own slot ([withSignature]) and leaves every
 * other slot as it received it, so a check that a transaction is safe to sign that way has to read
 * the same envelope the splice writes into — a second, parallel parse could agree with itself and
 * still describe different bytes.
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
     * What makes the splice safe: a slot already carrying bytes is somebody's signature over some
     * message, and this transaction would be broadcast carrying it.
     */
    val isUnsigned: Boolean
        get() =
            (firstSignatureOffset until
                    firstSignatureOffset + requiredSignatures * SIGNATURE_LENGTH)
                .all { bytes[it] == ZERO_BYTE }

    /**
     * [bytes] with [signature] written into the slot that belongs to [signer], a 32-byte ed25519
     * public key, and nothing else touched: the message and every other signer's slot come out
     * exactly as they went in, so a co-signer's signature that was already there is broadcast
     * alongside this one.
     *
     * @throws IllegalStateException if [signature] is not one signature long, or for anything
     *   [signatureOffsetOf] refuses.
     */
    fun withSignature(signer: ByteArray, signature: ByteArray): ByteArray {
        check(signature.size == SIGNATURE_LENGTH) { "Unexpected Solana signature length" }
        val offset = signatureOffsetOf(signer)
        return bytes.copyOf().also { signature.copyInto(it, destinationOffset = offset) }
    }

    /**
     * The offset in [bytes] of the signature slot that belongs to [signer].
     *
     * Solana verifies `signatures[i]` against the i-th static account key of the message, so a
     * signer's slot is wherever the message lists its key among the first `numRequiredSignatures`
     * keys — slot 0 only when it is the fee payer. A sponsored or multi-signer transaction puts the
     * vault at index 1 or later, and a signature written to slot 0 there would sit in the fee
     * payer's slot while the vault's own stayed empty.
     *
     * @throws IllegalStateException if the message header is malformed, if the envelope declares a
     *   different number of slots than the header requires (the runtime refuses such a transaction
     *   outright), or if [signer] is not a required signer of this message — there is no slot of
     *   its own to fill, and filling anybody else's would overwrite that party's signature.
     */
    private fun signatureOffsetOf(signer: ByteArray): Int {
        require(signer.size == PUBLIC_KEY_LENGTH) { "Solana signer key must be 32 bytes" }
        val headerOffset = messageHeaderOffset(message)
        check(message.size >= headerOffset + HEADER_LENGTH) {
            "Solana message too short for its header"
        }
        val numRequiredSignatures = message[headerOffset].toInt() and 0xFF
        check(numRequiredSignatures == requiredSignatures) {
            "Solana transaction declares $requiredSignatures signature slot(s) but its message " +
                "requires $numRequiredSignatures"
        }
        val (keyCount, keysOffset) = readCompactU16(message, start = headerOffset + HEADER_LENGTH)
        check(
            keyCount >= numRequiredSignatures &&
                message.size >= keysOffset + numRequiredSignatures * PUBLIC_KEY_LENGTH
        ) {
            "Solana message too short for its $numRequiredSignatures required signer(s)"
        }
        val index =
            (0 until numRequiredSignatures).firstOrNull { i ->
                val start = keysOffset + i * PUBLIC_KEY_LENGTH
                message.copyOfRange(start, start + PUBLIC_KEY_LENGTH).contentEquals(signer)
            } ?: error("Solana transaction does not require a signature from this vault")
        return firstSignatureOffset + index * SIGNATURE_LENGTH
    }

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

        private const val PUBLIC_KEY_LENGTH = 32

        /** `numRequiredSignatures`, `numReadonlySignedAccounts`, `numReadonlyUnsignedAccounts`. */
        private const val HEADER_LENGTH = 3

        /** Set on the first byte of a versioned message; a legacy header byte never reaches it. */
        private const val VERSION_PREFIX_BIT = 0x80

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
         * Where the 3-byte header of [message] begins: at 0 for a legacy message, or past the
         * version prefix byte (`0x80 | version`) of a versioned one. Only version 0 lives behind
         * this envelope — v1 lays the whole transaction out differently and never parses as one.
         */
        private fun messageHeaderOffset(message: ByteArray): Int {
            check(message.isNotEmpty()) { "Solana transaction carries no message" }
            val first = message[0].toInt() and 0xFF
            if (first and VERSION_PREFIX_BIT == 0) return 0
            val version = first and VERSION_PREFIX_BIT.inv()
            check(version == 0) { "Unsupported Solana message version $version" }
            return 1
        }

        /**
         * Decodes the Solana compact-u16 (shortvec) at [start] in [bytes] — up to three bytes, 7
         * payload bits each with the high bit signalling "more bytes follow" — and returns the
         * decoded value together with the offset just past it (where the items it counts begin).
         */
        private fun readCompactU16(bytes: ByteArray, start: Int = 0): Pair<Int, Int> {
            var value = 0
            var offset = start
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

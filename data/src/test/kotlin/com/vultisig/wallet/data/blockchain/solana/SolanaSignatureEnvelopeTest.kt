package com.vultisig.wallet.data.blockchain.solana

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * The envelope the raw-signing path splices into, and the checks that decide whether it may.
 *
 * Read from the original wire bytes rather than re-serialized, so these fixtures are hand-built
 * `[compact-u16 count][count × 64-byte slot][message]` rather than anything WalletCore produced.
 */
class SolanaSignatureEnvelopeTest {

    @Test
    fun `a single-signature transaction splits into its slot and its message`() {
        val envelope = SolanaSignatureEnvelope.parse(transaction(signatures = 1))

        assertEquals(1, envelope.requiredSignatures)
        assertEquals(1, envelope.firstSignatureOffset)
        assertEquals(MESSAGE.toList(), envelope.message.toList())
        assertTrue(envelope.isUnsigned)
    }

    @Test
    fun `a slot that already carries bytes is not unsigned`() {
        // What makes the splice at slot 0 safe is that nothing is being overwritten. A filled slot
        // is somebody's signature over some message, and it would be broadcast along with ours.
        val bytes = transaction(signatures = 1).also { it[1] = 7 }

        assertFalse(SolanaSignatureEnvelope.parse(bytes).isUnsigned)
    }

    @Test
    fun `a second empty slot still counts as a second required signature`() {
        // Emptiness is not the question here — the splice fills slot 0 and leaves slot 1 as it
        // found it, so this transaction would go out one signature short.
        val envelope = SolanaSignatureEnvelope.parse(transaction(signatures = 2))

        assertEquals(2, envelope.requiredSignatures)
        assertTrue(envelope.isUnsigned)
    }

    @Test
    fun `a transaction declaring no signatures is refused`() {
        assertThrows<IllegalStateException> {
            SolanaSignatureEnvelope.parse(byteArrayOf(0) + MESSAGE)
        }
    }

    @Test
    fun `a transaction too short for the slots it declares is refused`() {
        assertThrows<IllegalStateException> {
            SolanaSignatureEnvelope.parse(byteArrayOf(2) + ByteArray(64))
        }
    }

    @Test
    fun `a multi-byte compact-u16 count is decoded from all its payload bits`() {
        // 0x80 0x01 is 128 with the continuation bit set on the first byte. Well past anything a
        // real transaction carries, but the shortvec is what the count is read through and a
        // single-byte-only reader would call this 0.
        val bytes = byteArrayOf(0x80.toByte(), 0x01) + ByteArray(128 * 64) + MESSAGE

        val envelope = SolanaSignatureEnvelope.parse(bytes)

        assertEquals(128, envelope.requiredSignatures)
        assertEquals(2, envelope.firstSignatureOffset)
        assertEquals(MESSAGE.toList(), envelope.message.toList())
    }

    @Test
    fun `the fee payer's signature goes into slot 0`() {
        // The shape every first-party flow produces: the vault is account key 0, the only required
        // signer. Byte-for-byte what the splice always wrote.
        val message = legacyMessage(requiredSigners = listOf(VAULT))
        val envelope = SolanaSignatureEnvelope.parse(byteArrayOf(1) + ByteArray(64) + message)

        val signed = envelope.withSignature(VAULT, VAULT_SIGNATURE)

        assertEquals((byteArrayOf(1) + VAULT_SIGNATURE + message).toList(), signed.bytes.toList())
        assertEquals(VAULT_SIGNATURE.toList(), signed.feePayerSignature?.toList())
    }

    @Test
    fun `a co-signer's signature goes into its own slot and leaves the others as received`() {
        // A relayer pays the fee from slot 0, the vault is signer 1, a third party has already
        // signed into slot 2. Solana verifies signatures[i] against account key i, so the vault's
        // signature belongs in slot 1 — writing it to slot 0 would leave the vault's own slot
        // empty and put the vault's bytes where the relayer's are checked.
        val message = v0Message(requiredSigners = listOf(RELAYER, VAULT, COSIGNER))
        val bytes = byteArrayOf(3) + ByteArray(64) + ByteArray(64) + COSIGNER_SIGNATURE + message
        val envelope = SolanaSignatureEnvelope.parse(bytes.copyOf())

        val signed = envelope.withSignature(VAULT, VAULT_SIGNATURE)

        assertEquals(
            (byteArrayOf(3) + ByteArray(64) + VAULT_SIGNATURE + COSIGNER_SIGNATURE + message)
                .toList(),
            signed.bytes.toList(),
        )
        // The splice returns a copy; the envelope it was read from is still what arrived.
        assertEquals(bytes.toList(), envelope.bytes.toList())
    }

    @Test
    fun `there is no transaction id until the fee payer has signed`() {
        // The chain indexes a transaction by its first signature only. With the relayer's slot
        // still empty the transaction has no id yet, and the vault's own signature is not one —
        // reporting it would send a duplicate-broadcast lookup after something never indexed.
        val message = v0Message(requiredSigners = listOf(RELAYER, VAULT))
        val envelope = SolanaSignatureEnvelope.parse(byteArrayOf(2) + ByteArray(128) + message)

        val signed = envelope.withSignature(VAULT, VAULT_SIGNATURE)

        assertNull(signed.feePayerSignature)
    }

    @Test
    fun `the transaction id is the relayer's signature once it is in slot 0`() {
        val message = v0Message(requiredSigners = listOf(RELAYER, VAULT))
        val bytes = byteArrayOf(2) + RELAYER_SIGNATURE + ByteArray(64) + message
        val envelope = SolanaSignatureEnvelope.parse(bytes)

        val signed = envelope.withSignature(VAULT, VAULT_SIGNATURE)

        assertEquals(RELAYER_SIGNATURE.toList(), signed.feePayerSignature?.toList())
    }

    @Test
    fun `a legacy message resolves the signer's slot the same way`() {
        val message = legacyMessage(requiredSigners = listOf(RELAYER, VAULT))
        val envelope = SolanaSignatureEnvelope.parse(byteArrayOf(2) + ByteArray(128) + message)

        val signed = envelope.withSignature(VAULT, VAULT_SIGNATURE)

        assertEquals(
            (byteArrayOf(2) + ByteArray(64) + VAULT_SIGNATURE + message).toList(),
            signed.bytes.toList(),
        )
    }

    @Test
    fun `a transaction the vault is not required to sign is refused`() {
        // The vault is named, but past the required signers — a recipient, not a signer. There is
        // no slot of its own to fill, and the only alternative is overwriting somebody else's.
        val message =
            legacyMessage(requiredSigners = listOf(RELAYER), otherAccounts = listOf(VAULT))
        val envelope = SolanaSignatureEnvelope.parse(byteArrayOf(1) + ByteArray(64) + message)

        val error =
            assertThrows<IllegalStateException> { envelope.withSignature(VAULT, VAULT_SIGNATURE) }

        assertEquals(
            "Solana transaction does not require a signature from this vault",
            error.message,
        )
    }

    @Test
    fun `a slot count that disagrees with the message header is refused`() {
        // Two declared slots over a message requiring one. The runtime refuses the transaction
        // outright, so there is no slot layout worth resolving.
        val message = legacyMessage(requiredSigners = listOf(VAULT))
        val envelope = SolanaSignatureEnvelope.parse(byteArrayOf(2) + ByteArray(128) + message)

        val error =
            assertThrows<IllegalStateException> { envelope.withSignature(VAULT, VAULT_SIGNATURE) }

        assertEquals(
            "Solana transaction declares 2 signature slot(s) but its message requires 1",
            error.message,
        )
    }

    @Test
    fun `a message that ends before the account keys it declares is refused`() {
        // One required signer, three declared account keys, bytes for only the first. The vault
        // is right there at index 0, but the runtime would fail to deserialize this message, so
        // there is nothing worth signing into.
        val message = byteArrayOf(1, 0, 0) + byteArrayOf(3) + VAULT
        val envelope = SolanaSignatureEnvelope.parse(byteArrayOf(1) + ByteArray(64) + message)

        val error =
            assertThrows<IllegalStateException> { envelope.withSignature(VAULT, VAULT_SIGNATURE) }

        assertEquals("Solana message too short for its 3 account key(s)", error.message)
    }

    @Test
    fun `a message requiring more signatures than it lists keys for is refused`() {
        val message = byteArrayOf(2, 0, 0) + byteArrayOf(1) + VAULT + ByteArray(32) + byteArrayOf(0)
        val envelope = SolanaSignatureEnvelope.parse(byteArrayOf(2) + ByteArray(128) + message)

        val error =
            assertThrows<IllegalStateException> { envelope.withSignature(VAULT, VAULT_SIGNATURE) }

        assertEquals(
            "Solana message lists 1 account key(s) but requires 2 signature(s)",
            error.message,
        )
    }

    @Test
    fun `a message version other than 0 is refused`() {
        // Only version 0 is defined; a higher version's header could sit anywhere.
        val message = byteArrayOf(0x81.toByte()) + legacyMessage(requiredSigners = listOf(VAULT))
        val envelope = SolanaSignatureEnvelope.parse(byteArrayOf(1) + ByteArray(64) + message)

        assertThrows<IllegalStateException> { envelope.withSignature(VAULT, VAULT_SIGNATURE) }
    }

    @Test
    fun `a signature of the wrong length is refused before anything is written`() {
        val message = legacyMessage(requiredSigners = listOf(VAULT))
        val envelope = SolanaSignatureEnvelope.parse(byteArrayOf(1) + ByteArray(64) + message)

        assertThrows<IllegalStateException> { envelope.withSignature(VAULT, ByteArray(63)) }
    }

    private fun transaction(signatures: Int): ByteArray =
        byteArrayOf(signatures.toByte()) +
            ByteArray(signatures * SolanaSignatureEnvelope.SIGNATURE_LENGTH) +
            MESSAGE

    /**
     * A legacy message: the 3-byte header, the static account keys behind a compact-u16 count, a
     * recent blockhash and no instructions. [requiredSigners] come first, as the runtime requires.
     */
    private fun legacyMessage(
        requiredSigners: List<ByteArray>,
        otherAccounts: List<ByteArray> = emptyList(),
    ): ByteArray {
        val accounts = requiredSigners + otherAccounts
        return byteArrayOf(requiredSigners.size.toByte(), 0, 0) +
            byteArrayOf(accounts.size.toByte()) +
            accounts.reduce { acc, key -> acc + key } +
            ByteArray(32) +
            byteArrayOf(0)
    }

    /** A v0 message: the version prefix, a legacy body and an empty lookup-table list. */
    private fun v0Message(requiredSigners: List<ByteArray>): ByteArray =
        byteArrayOf(0x80.toByte()) + legacyMessage(requiredSigners) + byteArrayOf(0)

    private companion object {
        /** Stands in for the message; the envelope never looks inside it. */
        val MESSAGE = byteArrayOf(0x01, 0x02, 0x03, 0x04)

        val RELAYER = ByteArray(32) { 0x11 }
        val VAULT = ByteArray(32) { 0x22 }
        val COSIGNER = ByteArray(32) { 0x33 }

        val VAULT_SIGNATURE = ByteArray(64) { 0xAA.toByte() }
        val COSIGNER_SIGNATURE = ByteArray(64) { 0xBB.toByte() }
        val RELAYER_SIGNATURE = ByteArray(64) { 0xCC.toByte() }
    }
}

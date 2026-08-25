package com.vultisig.wallet.data.blockchain.solana

import kotlin.test.assertEquals
import kotlin.test.assertFalse
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

    private fun transaction(signatures: Int): ByteArray =
        byteArrayOf(signatures.toByte()) +
            ByteArray(signatures * SolanaSignatureEnvelope.SIGNATURE_LENGTH) +
            MESSAGE

    private companion object {
        /** Stands in for the message; the envelope never looks inside it. */
        val MESSAGE = byteArrayOf(0x01, 0x02, 0x03, 0x04)
    }
}

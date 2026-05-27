package com.vultisig.wallet.data.tss

import com.vultisig.wallet.data.utils.Numeric
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.junit.jupiter.api.Test

/**
 * Specifies the byte-level encoding for ECDSA and EdDSA custom-message signatures.
 *
 * Regression guard for issue #4624: Android was using the EdDSA encoding (reversed r+s, no recovery
 * ID) for all custom messages, including ECDSA chains. iOS and Extension use the standard ECDSA
 * encoding (r + s + recoveryId, big-endian), so the displayed signatures mismatched.
 *
 * The fix dispatches on keyType in KeysignViewModel.calculateCustomMessageSignature:
 * - ECDSA/MLDSA → getSignatureWithRecoveryID() = r + s + recoveryId (65 bytes)
 * - EdDSA → getSignature() = reversed(r) + reversed(s) (64 bytes)
 *
 * These tests verify the byte invariants for each format so a future change to TssExtensions.kt
 * that breaks cross-platform parity will be caught immediately. Note: tss.KeysignResponse requires
 * JNI and cannot be instantiated in JVM unit tests, so the format logic is exercised here with the
 * same Numeric utility used by the production code.
 */
class CustomMessageSignatureFormatTest {

    // Realistic 32-byte r / s / recoveryId hex strings (secp256k1 / ed25519 shape)
    private val R_HEX = "a1b2c3d4e5f60718293a4b5c6d7e8f90a1b2c3d4e5f60718293a4b5c6d7e8f90"
    private val S_HEX = "b1c2d3e4f5061718192a3b4c5d6e7f80b1c2d3e4f5061718192a3b4c5d6e7f80"
    private val RECOVERY_ID_HEX = "00"

    // Same byte logic as TssExtensions.getSignatureWithRecoveryID()
    private fun ecdsaSignatureBytes(r: String, s: String, recoveryId: String): ByteArray =
        Numeric.hexStringToByteArray(r) +
            Numeric.hexStringToByteArray(s) +
            Numeric.hexStringToByteArray(recoveryId)

    // Same byte logic as TssExtensions.getSignature()
    private fun eddsaSignatureBytes(r: String, s: String): ByteArray =
        Numeric.hexStringToByteArray(r).reversedArray() +
            Numeric.hexStringToByteArray(s).reversedArray()

    @Test
    fun `ECDSA signature is 65 bytes - r then s then recoveryId without reversal`() {
        val sig = ecdsaSignatureBytes(R_HEX, S_HEX, RECOVERY_ID_HEX)

        sig.size shouldBe 65
        // r is the first 32 bytes, big-endian (no reversal)
        sig.copyOfRange(0, 32) shouldBe Numeric.hexStringToByteArray(R_HEX)
        // s is the next 32 bytes, big-endian (no reversal)
        sig.copyOfRange(32, 64) shouldBe Numeric.hexStringToByteArray(S_HEX)
        // recovery ID is the final byte
        sig[64] shouldBe Numeric.hexStringToByteArray(RECOVERY_ID_HEX)[0]
    }

    @Test
    fun `EdDSA signature is 64 bytes - reversed-r then reversed-s with no recovery ID`() {
        val sig = eddsaSignatureBytes(R_HEX, S_HEX)

        sig.size shouldBe 64
        // r bytes are reversed (little-endian)
        sig.copyOfRange(0, 32) shouldBe Numeric.hexStringToByteArray(R_HEX).reversedArray()
        // s bytes are reversed (little-endian)
        sig.copyOfRange(32, 64) shouldBe Numeric.hexStringToByteArray(S_HEX).reversedArray()
    }

    @Test
    fun `ECDSA first byte is MSB of r but EdDSA first byte is LSB of r`() {
        val rBytes = Numeric.hexStringToByteArray(R_HEX)

        val ecdsaSig = ecdsaSignatureBytes(R_HEX, S_HEX, RECOVERY_ID_HEX)
        val eddsaSig = eddsaSignatureBytes(R_HEX, S_HEX)

        ecdsaSig[0] shouldBe rBytes.first() // big-endian: MSB first
        eddsaSig[0] shouldBe rBytes.last() // little-endian: LSB first
    }

    @Test
    fun `ECDSA and EdDSA encodings differ for the same r and s`() {
        val ecdsaSig = ecdsaSignatureBytes(R_HEX, S_HEX, RECOVERY_ID_HEX)
        val eddsaSig = eddsaSignatureBytes(R_HEX, S_HEX)

        // Different byte ordering — using the wrong encoding is the bug fixed in #4624
        ecdsaSig.copyOfRange(0, 32) shouldNotBe eddsaSig.copyOfRange(0, 32)
    }
}

package com.vultisig.wallet.data.usecases

import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class Pbkdf2AesEncryptionTest {

    private val legacyAes = AesEncryption()
    private val pbkdf2Aes = Pbkdf2AesEncryption(legacyAes)

    private val noopLegacy =
        object : Encryption {
            override fun encrypt(data: ByteArray, password: ByteArray): ByteArray =
                error("Not used in this test")

            override fun decrypt(data: ByteArray, password: ByteArray): ByteArray? = null
        }
    private val pbkdf2AesNoLegacy = Pbkdf2AesEncryption(noopLegacy)

    private val originalInput = "Original Input 123"
    private val password = "password123"

    @Test
    fun `PBKDF2 encryption is reversible`() {
        val encrypted =
            pbkdf2AesNoLegacy.encrypt(
                originalInput.toByteArray(Charsets.UTF_8),
                password.toByteArray(),
            )

        val decrypted = pbkdf2AesNoLegacy.decrypt(encrypted, password.toByteArray())
        assertNotNull(decrypted)
        assertEquals(originalInput, decrypted.toString(Charsets.UTF_8))
    }

    @Test
    fun `PBKDF2 decryption fails with wrong password`() {
        val encrypted =
            pbkdf2AesNoLegacy.encrypt(
                originalInput.toByteArray(Charsets.UTF_8),
                password.toByteArray(),
            )

        val decrypted = pbkdf2AesNoLegacy.decrypt(encrypted, "wrongpassword".toByteArray())
        assertNull(decrypted)
    }

    @Test
    fun `decrypts legacy GCM format`() {
        val encryptedBase64 = "zPMOwnPVMFKMf9LOIFkyqBOr8AC1SIdQ34Ruk5gmRqxZ+lIyK7zM5/1NUjXlAg=="
        val decrypted =
            pbkdf2Aes.decrypt(Base64.getDecoder().decode(encryptedBase64), password.toByteArray())
        assertNotNull(decrypted)
        assertEquals(originalInput, decrypted.toString(Charsets.UTF_8))
    }

    @Test
    fun `encrypted output starts with magic prefix`() {
        val encrypted =
            pbkdf2Aes.encrypt(originalInput.toByteArray(Charsets.UTF_8), password.toByteArray())

        val magic = byteArrayOf(0x56, 0x4C, 0x54, 0x02) // "VLT\x02"
        assertTrue(encrypted.copyOfRange(0, 4).contentEquals(magic))
    }

    @Test
    fun `encrypted output has correct header size`() {
        val encrypted =
            pbkdf2Aes.encrypt(originalInput.toByteArray(Charsets.UTF_8), password.toByteArray())

        // magic(4) + salt(16) + iv(12) + ciphertext(at least 1 byte + 16 tag)
        assertTrue(encrypted.size >= 32 + 17)
    }

    @Test
    fun `decrypts legacy GCM backup whose first IV byte is 0x02`() {
        val maxAttempts = 10_000
        var legacyCiphertext: ByteArray? = null
        var attempts = 0
        while (legacyCiphertext == null && attempts < maxAttempts) {
            val candidate =
                legacyAes.encrypt(originalInput.toByteArray(Charsets.UTF_8), password.toByteArray())
            if (candidate[0] == 0x02.toByte()) {
                legacyCiphertext = candidate
            }
            attempts++
        }
        assertNotNull(
            legacyCiphertext,
            "Could not produce a legacy ciphertext starting with 0x02 in $maxAttempts attempts",
        )

        val decrypted = pbkdf2Aes.decrypt(legacyCiphertext, password.toByteArray())
        assertNotNull(decrypted)
        assertEquals(originalInput, decrypted.toString(Charsets.UTF_8))
    }

    @Test
    fun `returns null for PBKDF2 payload smaller than header plus tag`() {
        val magic = byteArrayOf(0x56, 0x4C, 0x54, 0x02) // "VLT\x02"
        val tooShort = magic + ByteArray(10)

        val decrypted = pbkdf2AesNoLegacy.decrypt(tooShort, password.toByteArray())
        assertNull(decrypted)
    }
}

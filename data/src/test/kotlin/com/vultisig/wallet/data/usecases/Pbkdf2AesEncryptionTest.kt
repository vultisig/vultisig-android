package com.vultisig.wallet.data.usecases

import io.ktor.util.decodeBase64Bytes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class Pbkdf2AesEncryptionTest {

    private val legacyAes = AesEncryption()
    private val pbkdf2Aes = Pbkdf2AesEncryption(legacyAes)

    private val originalInput = "Original Input 123"
    private val password = "password123"

    @Test
    fun `PBKDF2 encryption is reversible`() {
        val encrypted =
            pbkdf2Aes.encrypt(originalInput.toByteArray(Charsets.UTF_8), password.toByteArray())

        val decrypted = pbkdf2Aes.decrypt(encrypted, password.toByteArray())
        assertNotNull(decrypted)
        assertEquals(originalInput, decrypted.toString(Charsets.UTF_8))
    }

    @Test
    fun `PBKDF2 decryption fails with wrong password`() {
        val encrypted =
            pbkdf2Aes.encrypt(originalInput.toByteArray(Charsets.UTF_8), password.toByteArray())

        try {
            val decrypted = pbkdf2Aes.decrypt(encrypted, "wrongpassword".toByteArray())
            // If legacy fallback is available (Android), it should return null
            assertNull(decrypted)
        } catch (_: Exception) {
            // Legacy fallback may throw on JVM (PKCS7PADDING unavailable)
        }
    }

    @Test
    fun `decrypts legacy GCM format`() {
        val encryptedBase64 = "zPMOwnPVMFKMf9LOIFkyqBOr8AC1SIdQ34Ruk5gmRqxZ+lIyK7zM5/1NUjXlAg=="
        val decrypted =
            pbkdf2Aes.decrypt(encryptedBase64.decodeBase64Bytes(), password.toByteArray())
        assertNotNull(decrypted)
        assertEquals(originalInput, decrypted.toString(Charsets.UTF_8))
    }

    @Test
    fun `encrypted output starts with version byte`() {
        val encrypted =
            pbkdf2Aes.encrypt(originalInput.toByteArray(Charsets.UTF_8), password.toByteArray())

        assertEquals(0x02.toByte(), encrypted[0])
    }

    @Test
    fun `encrypted output has correct header size`() {
        val encrypted =
            pbkdf2Aes.encrypt(originalInput.toByteArray(Charsets.UTF_8), password.toByteArray())

        // version(1) + salt(16) + iv(12) + ciphertext(at least 1 byte + 16 tag)
        assert(encrypted.size >= 29 + 17)
    }
}

package com.vultisig.wallet.data.usecases

import io.ktor.util.decodeBase64Bytes
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
        assertTrue(encrypted.size >= 29 + 17)
    }
}

package com.vultisig.wallet.data.usecases

import io.ktor.util.decodeBase64Bytes
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.test.assertFailsWith

class AesEncryptionTest {

    private val aes = AesEncryption()

    private val originalInput = "Original Input 123"
    private val password = "password123"

    @Test
    fun `encryption is reversible`() {
        val encrypted = aes.encrypt(originalInput.toByteArray(Charsets.UTF_8), password)

        assertEquals(
            originalInput,
            aes.decrypt(encrypted, password)!!.toString(Charsets.UTF_8),
        )
    }

    @Test
    fun `decryption works`() {
        val encryptedBase64 = "zPMOwnPVMFKMf9LOIFkyqBOr8AC1SIdQ34Ruk5gmRqxZ+lIyK7zM5/1NUjXlAg=="
        assertEquals(
            originalInput,
            aes.decrypt(encryptedBase64.decodeBase64Bytes(), password)!!.toString(Charsets.UTF_8),
        )
    }

    @Test
    fun `decryption fails if password isn't correct`() {
        val encrypted = aes.encrypt(originalInput.toByteArray(Charsets.UTF_8), password)

        assertFailsWith<Exception> {
            aes.decrypt(encrypted, "321drowssap")!!.toString(Charsets.UTF_8)
        }
    }

}
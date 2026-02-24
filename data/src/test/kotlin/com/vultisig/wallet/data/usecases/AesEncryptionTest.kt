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
        val encrypted =
            aes.encrypt(originalInput.toByteArray(Charsets.UTF_8), password.toByteArray())

        assertEquals(
            originalInput,
            aes.decrypt(encrypted, password.toByteArray())!!.toString(Charsets.UTF_8),
        )
    }

    @Test
    fun `decryption works`() {
        val encryptedBase64 = "zPMOwnPVMFKMf9LOIFkyqBOr8AC1SIdQ34Ruk5gmRqxZ+lIyK7zM5/1NUjXlAg=="
        assertEquals(
            originalInput,
            aes.decrypt(encryptedBase64.decodeBase64Bytes(), password.toByteArray())!!
                .toString(Charsets.UTF_8),
        )
    }

    @Test
    fun `decryption fails if password isn't correct`() {
        val encrypted =
            aes.encrypt(originalInput.toByteArray(Charsets.UTF_8), password.toByteArray())

        assertFailsWith<Exception> {
            aes.decrypt(encrypted, "321drowssap".toByteArray())!!.toString(Charsets.UTF_8)
        }
    }

    @OptIn(ExperimentalStdlibApi::class)
    @Test
    fun decryptMessageFromIOS() {
        val encryptionKey = "99bbc7c0941645762a688cb22efb1677865646c2c5b9706e940caf529c41ab19"
        val encryptedMessage = "CXzoWhNMozIdFIh7YzbXSm26QRrwtrAviVEk1baXhQKeKD76tH8="
        val decryptedMsg =
            aes.decrypt(encryptedMessage.decodeBase64Bytes(), encryptionKey.hexToByteArray())!!
                .toString(Charsets.UTF_8)
        assertEquals("helloworld", decryptedMsg)
    }

    @Test
    fun decryptMessageFromServer() {
        val encryptionKey = "password"
        val encryptedMessage = "PMUgpdrUY/6MgbxVN7Juaw+FUqq/p/Da5HE6xVptbHWP3UGfomHSfjii6qoLj8Y="
        val decryptedMsg =
            aes.decrypt(
                encryptedMessage.decodeBase64Bytes(),
                encryptionKey.toByteArray(Charsets.UTF_8)
            )!!
                .toString(Charsets.UTF_8)
        assertEquals("vultiserver-message", decryptedMsg)
    }
}
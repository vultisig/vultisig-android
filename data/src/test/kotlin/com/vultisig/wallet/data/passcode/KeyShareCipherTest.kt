package com.vultisig.wallet.data.passcode

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

internal class KeyShareCipherTest {

    private val cipher = KeyShareCipher()
    private val dataKey = ByteArray(32) { it.toByte() }
    private val keyShare = "0402ab...a-realistic-looking-keyshare-blob"

    @Test
    fun `encrypt then decrypt round-trips`() {
        val encrypted = cipher.encrypt(keyShare, dataKey)

        assertEquals(keyShare, cipher.decrypt(encrypted, dataKey))
    }

    @Test
    fun `ciphertext is tagged and does not leak the plaintext`() {
        val encrypted = cipher.encrypt(keyShare, dataKey)

        assertTrue(cipher.isEncrypted(encrypted))
        assertFalse(encrypted.contains(keyShare))
    }

    @Test
    fun `plaintext passes through untouched for users without a passcode`() {
        assertFalse(cipher.isEncrypted(keyShare))
        assertEquals(keyShare, cipher.decrypt(keyShare, dataKey))
        assertEquals(keyShare, cipher.decrypt(keyShare, null))
    }

    @Test
    fun `decrypt returns null when the app is locked`() {
        val encrypted = cipher.encrypt(keyShare, dataKey)

        assertNull(cipher.decrypt(encrypted, null))
    }

    @Test
    fun `decrypt returns null for the wrong data key`() {
        val encrypted = cipher.encrypt(keyShare, dataKey)

        assertNull(cipher.decrypt(encrypted, ByteArray(32) { (it + 1).toByte() }))
    }

    @Test
    fun `decrypt returns null for tampered or truncated ciphertext`() {
        val encrypted = cipher.encrypt(keyShare, dataKey)

        assertNull(cipher.decrypt(encrypted.dropLast(4), dataKey))
        assertNull(cipher.decrypt("vlpc1:not-base64!!", dataKey))
        assertNull(cipher.decrypt("vlpc1:", dataKey))
    }

    @Test
    fun `each encryption uses a fresh IV`() {
        assertNotEquals(cipher.encrypt(keyShare, dataKey), cipher.encrypt(keyShare, dataKey))
    }

    @Test
    fun `empty keyshares round-trip`() {
        val encrypted = cipher.encrypt("", dataKey)

        assertEquals("", cipher.decrypt(encrypted, dataKey))
    }
}

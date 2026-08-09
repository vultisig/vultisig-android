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
    private val identity = KeyShareIdentity(vaultId = "vault-1", pubKey = "pub-1")

    @Test
    fun `encrypt then decrypt round-trips`() {
        val encrypted = cipher.encrypt(keyShare, dataKey, identity)

        assertEquals(keyShare, cipher.decrypt(encrypted, dataKey, identity))
    }

    @Test
    fun `ciphertext is tagged and does not leak the plaintext`() {
        val encrypted = cipher.encrypt(keyShare, dataKey, identity)

        assertTrue(cipher.isEncrypted(encrypted))
        assertFalse(encrypted.contains(keyShare))
    }

    @Test
    fun `plaintext passes through untouched for users without a passcode`() {
        assertFalse(cipher.isEncrypted(keyShare))
        assertEquals(keyShare, cipher.decrypt(keyShare, dataKey, identity))
        assertEquals(keyShare, cipher.decrypt(keyShare, null, identity))
    }

    @Test
    fun `decrypt returns null when the app is locked`() {
        val encrypted = cipher.encrypt(keyShare, dataKey, identity)

        assertNull(cipher.decrypt(encrypted, null, identity))
    }

    @Test
    fun `decrypt returns null for the wrong data key`() {
        val encrypted = cipher.encrypt(keyShare, dataKey, identity)

        assertNull(cipher.decrypt(encrypted, ByteArray(32) { (it + 1).toByte() }, identity))
    }

    @Test
    fun `decrypt returns null for tampered or truncated ciphertext`() {
        val encrypted = cipher.encrypt(keyShare, dataKey, identity)

        assertNull(cipher.decrypt(encrypted.dropLast(4), dataKey, identity))
        assertNull(cipher.decrypt("vlpc1:not-base64!!", dataKey, identity))
        assertNull(cipher.decrypt("vlpc1:", dataKey, identity))
    }

    @Test
    fun `each encryption uses a fresh IV`() {
        assertNotEquals(
            cipher.encrypt(keyShare, dataKey, identity),
            cipher.encrypt(keyShare, dataKey, identity),
        )
    }

    @Test
    fun `empty keyshares round-trip`() {
        val encrypted = cipher.encrypt("", dataKey, identity)

        assertEquals("", cipher.decrypt(encrypted, dataKey, identity))
    }

    @Test
    fun `a ciphertext moved to another vault does not decrypt`() {
        // The row identity is authenticated, so copying a share between rows of the same table is
        // rejected rather than silently handing back another vault's keyshare.
        val encrypted = cipher.encrypt(keyShare, dataKey, identity)

        assertNull(cipher.decrypt(encrypted, dataKey, identity.copy(vaultId = "vault-2")))
    }

    @Test
    fun `a ciphertext moved to another pubKey does not decrypt`() {
        val encrypted = cipher.encrypt(keyShare, dataKey, identity)

        assertNull(cipher.decrypt(encrypted, dataKey, identity.copy(pubKey = "pub-2")))
    }

    @Test
    fun `row identities that concatenate the same way are still distinct`() {
        // "a b"/"c" and "a"/"b c" must not produce the same AAD, or a share could be swapped
        // between those two rows undetected.
        val left = KeyShareIdentity(vaultId = "a b", pubKey = "c")
        val right = KeyShareIdentity(vaultId = "a", pubKey = "b c")
        val encrypted = cipher.encrypt(keyShare, dataKey, left)

        assertNull(cipher.decrypt(encrypted, dataKey, right))
    }
}

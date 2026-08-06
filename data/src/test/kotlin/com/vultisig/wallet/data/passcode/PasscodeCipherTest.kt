package com.vultisig.wallet.data.passcode

import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import org.junit.jupiter.api.Test

internal class PasscodeCipherTest {

    private val cipher = PasscodeCipher()

    @Test
    fun `unwrap recovers the data key wrapped under the same passcode`() {
        val salt = cipher.newSalt()
        val dataKey = cipher.newDataKey()

        val wrapped = cipher.wrap(dataKey, "12345", salt)

        assertContentEquals(dataKey, cipher.unwrap(wrapped, "12345", salt))
    }

    @Test
    fun `unwrap returns null for a wrong passcode`() {
        val salt = cipher.newSalt()
        val wrapped = cipher.wrap(cipher.newDataKey(), "12345", salt)

        assertNull(cipher.unwrap(wrapped, "54321", salt))
    }

    @Test
    fun `unwrap returns null when the salt does not match`() {
        val wrapped = cipher.wrap(cipher.newDataKey(), "12345", cipher.newSalt())

        assertNull(cipher.unwrap(wrapped, "12345", cipher.newSalt()))
    }

    @Test
    fun `unwrap returns null when the blob is truncated`() {
        val salt = cipher.newSalt()
        val wrapped = cipher.wrap(cipher.newDataKey(), "12345", salt)

        assertNull(cipher.unwrap(wrapped.copyOf(wrapped.size - 1), "12345", salt))
        assertNull(cipher.unwrap(ByteArray(0), "12345", salt))
    }

    @Test
    fun `unwrap returns null when a single ciphertext byte is flipped`() {
        val salt = cipher.newSalt()
        val wrapped = cipher.wrap(cipher.newDataKey(), "12345", salt)
        wrapped[wrapped.lastIndex] = (wrapped[wrapped.lastIndex] + 1).toByte()

        assertNull(cipher.unwrap(wrapped, "12345", salt))
    }

    @Test
    fun `wrapping the same key twice produces different blobs`() {
        val salt = cipher.newSalt()
        val dataKey = cipher.newDataKey()

        val first = cipher.wrap(dataKey, "12345", salt)
        val second = cipher.wrap(dataKey, "12345", salt)

        assertFalse(first.contentEquals(second), "GCM IV must be random per wrap")
        assertContentEquals(dataKey, cipher.unwrap(second, "12345", salt))
    }

    @Test
    fun `generated material has the expected size and is random`() {
        assertEquals(32, cipher.newDataKey().size)
        assertEquals(16, cipher.newSalt().size)
        assertNotEquals(
            cipher.newDataKey().toList(),
            cipher.newDataKey().toList(),
            "data keys must not repeat",
        )
    }
}

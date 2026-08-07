package com.vultisig.wallet.data.passcode

import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

internal class PasscodeCipherTest {

    private val cipher = PasscodeCipher()

    @Test
    fun `unwrap recovers the data key wrapped under the same passcode`() {
        val salt = cipher.newSalt()
        val dataKey = cipher.newDataKey()

        val wrapped = cipher.wrap(dataKey, "123456", salt)

        assertContentEquals(dataKey, cipher.unwrap(wrapped, "123456", salt))
    }

    @Test
    fun `unwrap returns null for a wrong passcode`() {
        val salt = cipher.newSalt()
        val wrapped = cipher.wrap(cipher.newDataKey(), "123456", salt)

        assertNull(cipher.unwrap(wrapped, "654321", salt))
    }

    @Test
    fun `unwrap returns null when the salt does not match`() {
        val wrapped = cipher.wrap(cipher.newDataKey(), "123456", cipher.newSalt())

        assertNull(cipher.unwrap(wrapped, "123456", cipher.newSalt()))
    }

    @Test
    fun `unwrap returns null when the blob is truncated`() {
        val salt = cipher.newSalt()
        val wrapped = cipher.wrap(cipher.newDataKey(), "123456", salt)

        assertNull(cipher.unwrap(wrapped.copyOf(wrapped.size - 1), "123456", salt))
        assertNull(cipher.unwrap(ByteArray(0), "123456", salt))
    }

    @Test
    fun `unwrap returns null when a single ciphertext byte is flipped`() {
        val salt = cipher.newSalt()
        val wrapped = cipher.wrap(cipher.newDataKey(), "123456", salt)
        wrapped[wrapped.lastIndex] = (wrapped[wrapped.lastIndex] + 1).toByte()

        assertNull(cipher.unwrap(wrapped, "123456", salt))
    }

    @Test
    fun `wrapping the same key twice produces different blobs`() {
        val salt = cipher.newSalt()
        val dataKey = cipher.newDataKey()

        val first = cipher.wrap(dataKey, "123456", salt)
        val second = cipher.wrap(dataKey, "123456", salt)

        assertFalse(first.contentEquals(second), "GCM IV must be random per wrap")
        assertContentEquals(dataKey, cipher.unwrap(second, "123456", salt))
    }

    /**
     * Pins the KDF cost. The wrapped key is only as expensive to attack as this number makes it,
     * and a well-meaning "unlock feels slow on old devices" tweak would weaken it without any test
     * noticing. 210k is the OWASP floor for PBKDF2-HMAC-SHA256.
     */
    @Test
    fun `key derivation uses at least the OWASP floor of PBKDF2 iterations`() {
        assertEquals("PBKDF2WithHmacSHA256", PasscodeCipher.PBKDF2_ALGORITHM)
        // A floor, not an equality: raising the cost is the change this test exists to protect,
        // and it should not have to be edited to let one through.
        assertTrue(
            PasscodeCipher.PBKDF2_ITERATIONS >= 210_000,
            "PBKDF2 iterations fell to ${PasscodeCipher.PBKDF2_ITERATIONS}, below the OWASP floor",
        )
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

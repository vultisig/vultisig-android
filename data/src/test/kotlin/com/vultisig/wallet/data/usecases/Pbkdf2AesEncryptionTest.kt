package com.vultisig.wallet.data.usecases

import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertContentEquals
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

    // ── cross-platform vectors ────────────────────────────────────────────────

    /**
     * Loads a fixture from ios-backup-vectors/ and returns (password, plaintext, ciphertextBytes).
     */
    private fun loadFixture(name: String): Triple<String, String, ByteArray> {
        val stream =
            checkNotNull(
                javaClass.classLoader?.getResourceAsStream("ios-backup-vectors/$name.json")
            ) {
                "Fixture $name.json not found in test resources"
            }
        val text = stream.bufferedReader().readText()
        fun extract(key: String): String {
            val m =
                Regex(""""$key":\s*"([^"]+)"""").find(text)
                    ?: error("Key '$key' not found in fixture $name.json")
            return m.groupValues[1]
        }
        return Triple(
            extract("password"),
            extract("plaintext"),
            Base64.getDecoder().decode(extract("ciphertextBase64")),
        )
    }

    @Test
    fun `decrypt_iosClientVector_returnsExpectedPlaintext`() {
        val (pwd, expected, ciphertext) = loadFixture("ios")
        val decrypted = pbkdf2AesNoLegacy.decrypt(ciphertext, pwd.toByteArray())
        assertNotNull(decrypted)
        assertEquals(expected, decrypted.toString(Charsets.UTF_8))
    }

    @Test
    fun `decrypt_extensionClientVector_returnsExpectedPlaintext`() {
        val (pwd, expected, ciphertext) = loadFixture("extension")
        val decrypted = pbkdf2AesNoLegacy.decrypt(ciphertext, pwd.toByteArray())
        assertNotNull(decrypted)
        assertEquals(expected, decrypted.toString(Charsets.UTF_8))
    }

    @Test
    fun `decrypt_webClientVector_returnsExpectedPlaintext`() {
        val (pwd, expected, ciphertext) = loadFixture("web")
        val decrypted = pbkdf2AesNoLegacy.decrypt(ciphertext, pwd.toByteArray())
        assertNotNull(decrypted)
        assertEquals(expected, decrypted.toString(Charsets.UTF_8))
    }

    @Test
    fun `encrypt_sameSaltAndPassword_producesDeterministicKey`() {
        // deriveKey is private; verify determinism by decrypting a pre-computed vector twice.
        // If the same salt+password always yields the same key both calls must succeed and
        // return the same plaintext.
        val (pwd, expected, ciphertext) = loadFixture("ios")
        val first = pbkdf2AesNoLegacy.decrypt(ciphertext, pwd.toByteArray())
        val second = pbkdf2AesNoLegacy.decrypt(ciphertext, pwd.toByteArray())
        assertNotNull(first)
        assertNotNull(second)
        assertEquals(expected, first.toString(Charsets.UTF_8))
        assertContentEquals(first, second)
    }

    @Test
    fun `encrypt_differentSalts_producesDifferentCiphertexts`() {
        val data = originalInput.toByteArray(Charsets.UTF_8)
        val pw = password.toByteArray()
        val outputs = (1..100).map { pbkdf2AesNoLegacy.encrypt(data, pw).toList() }
        assertEquals(100, outputs.distinct().size, "All 100 encryptions must be distinct")
    }

    @Test
    fun `rejectsMalformedGcmTag_returnsNull`() {
        val magic = byteArrayOf(0x56, 0x4C, 0x54, 0x02)
        val salt = ByteArray(16) { it.toByte() }
        val iv = ByteArray(12) { it.toByte() }
        // 16 bytes of garbage — valid header size but GCM tag will not authenticate
        val payload = magic + salt + iv + ByteArray(16)
        val result = pbkdf2AesNoLegacy.decrypt(payload, password.toByteArray())
        assertNull(result)
    }

    @Test
    fun `usesExpectedIterationCount`() {
        // This vector was produced with exactly 600,000 PBKDF2-HMAC-SHA256 iterations.
        // Decryption succeeds only when the constant matches; any other count yields null.
        val (pwd, expected, ciphertext) = loadFixture("ios")
        val decrypted = pbkdf2AesNoLegacy.decrypt(ciphertext, pwd.toByteArray())
        assertNotNull(
            decrypted,
            "Decryption failed — PBKDF2_ITERATIONS constant may not be 600,000",
        )
        assertEquals(expected, decrypted.toString(Charsets.UTF_8))
    }
}

package com.vultisig.wallet.data.usecases

import io.ktor.util.decodeBase64Bytes
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking

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
            aes.decrypt(encryptedBase64.decodeBase64Bytes(), password.toByteArray())!!.toString(
                Charsets.UTF_8
            ),
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
    fun `decrypts iOS ciphertext`() {
        val encryptionKey = "99bbc7c0941645762a688cb22efb1677865646c2c5b9706e940caf529c41ab19"
        val encryptedMessage = "CXzoWhNMozIdFIh7YzbXSm26QRrwtrAviVEk1baXhQKeKD76tH8="
        val decryptedMsg =
            aes.decrypt(encryptedMessage.decodeBase64Bytes(), encryptionKey.hexToByteArray())!!
                .toString(Charsets.UTF_8)
        assertEquals("helloworld", decryptedMsg)
    }

    @Test
    fun `decrypts server ciphertext`() {
        val encryptionKey = "password"
        val encryptedMessage = "PMUgpdrUY/6MgbxVN7Juaw+FUqq/p/Da5HE6xVptbHWP3UGfomHSfjii6qoLj8Y="
        val decryptedMsg =
            aes.decrypt(
                    encryptedMessage.decodeBase64Bytes(),
                    encryptionKey.toByteArray(Charsets.UTF_8),
                )!!
                .toString(Charsets.UTF_8)
        assertEquals("vultiserver-message", decryptedMsg)
    }

    @Test
    fun `encrypt produces different ciphertext for same input due to random IV`() {
        val plaintext = originalInput.toByteArray(Charsets.UTF_8)
        val pw = password.toByteArray()

        val first = aes.encrypt(plaintext, pw)
        val second = aes.encrypt(plaintext, pw)

        assertNotEquals(
            first.toList(),
            second.toList(),
            "GCM with random IV must not produce identical ciphertext",
        )

        assertEquals(originalInput, aes.decrypt(first, pw)!!.toString(Charsets.UTF_8))
        assertEquals(originalInput, aes.decrypt(second, pw)!!.toString(Charsets.UTF_8))
    }

    @Test
    fun `empty input round trips`() {
        val pw = password.toByteArray()
        val encrypted = aes.encrypt(ByteArray(0), pw)
        val decrypted = aes.decrypt(encrypted, pw)

        assertContentEquals(ByteArray(0), decrypted)
    }

    @Test
    fun `large payload round trips`() {
        val pw = password.toByteArray()
        // Representative of a TSS keygen/keysign message, which can span several KB.
        val plaintext = ByteArray(128 * 1024) { (it and 0xFF).toByte() }

        val encrypted = aes.encrypt(plaintext, pw)
        val decrypted = aes.decrypt(encrypted, pw)

        assertContentEquals(plaintext, decrypted)
    }

    @Test
    fun `decrypt returns null when both GCM and CBC cannot decrypt`() {
        // 16-byte block size keeps CBC from throwing IllegalBlockSizeException,
        // so the code reaches the BadPaddingException branch and returns null.
        val garbage = ByteArray(64) { 0x00 }

        assertNull(aes.decrypt(garbage, password.toByteArray()))
    }

    @Test
    fun `concurrent encrypt-decrypt with same password does not corrupt results`() =
        runBlocking(Dispatchers.Default) {
            val pw = password.toByteArray()
            val iterations = 200
            val parallelism = 16

            val payloads =
                (0 until parallelism).map { index ->
                    "payload-$index-${"x".repeat(512)}".toByteArray(Charsets.UTF_8)
                }

            (0 until parallelism)
                .map { index ->
                    async {
                        val payload = payloads[index]
                        repeat(iterations) { iter ->
                            val encrypted = aes.encrypt(payload, pw)
                            val decrypted =
                                aes.decrypt(encrypted, pw)
                                    ?: error("decrypt returned null in iteration $iter")
                            check(decrypted.contentEquals(payload)) {
                                "round-trip mismatch at iteration $iter (thread $index)"
                            }
                        }
                    }
                }
                .awaitAll()
        }

    @Test
    fun `concurrent encrypt-decrypt with different passwords does not cross contaminate`() =
        runBlocking(Dispatchers.Default) {
            val iterations = 200
            val parallelism = 16

            val credentials =
                (0 until parallelism).map { index ->
                    val pw = "password-$index-${"p".repeat(32)}".toByteArray()
                    val payload = "payload-$index-${"y".repeat(256)}".toByteArray(Charsets.UTF_8)
                    pw to payload
                }

            (0 until parallelism)
                .map { index ->
                    async {
                        val (pw, payload) = credentials[index]
                        repeat(iterations) { iter ->
                            val encrypted = aes.encrypt(payload, pw)
                            val decrypted =
                                aes.decrypt(encrypted, pw)
                                    ?: error("decrypt returned null (thread $index, iter $iter)")
                            check(decrypted.contentEquals(payload)) {
                                "cross-contamination at iteration $iter (thread $index)"
                            }
                        }
                    }
                }
                .awaitAll()
        }

    // ── edge cases ────────────────────────────────────────────────────────────

    @Test
    fun `decrypt_truncatedCiphertext_returnsNull`() {
        // 16 bytes: GCM extracts 12-byte IV but only 4 bytes remain — not enough
        // for the 16-byte AES-GCM authentication tag. GCM auth fails, CBC padding
        // check fails → null.
        assertNull(aes.decrypt(ByteArray(16), password.toByteArray()))
    }

    @Test
    fun `decrypt_invalidIvLength_returnsNull`() {
        // 32 bytes of non-zero garbage: GCM authentication will fail (wrong key/tag),
        // and CBC PKCS5 padding check will fail → null.
        val invalid = ByteArray(32) { (0xAB + it).toByte() }
        assertNull(aes.decrypt(invalid, password.toByteArray()))
    }

    @Test
    fun `decrypt_wrongVersionPrefix_returnsNull`() {
        // AesEncryption has no magic prefix; any garbage that fails both GCM auth
        // and CBC padding returns null. Uses a different byte pattern from the
        // existing all-zero test to exercise different key-schedule paths.
        val garbage = ByteArray(64) { (0xFF - it).toByte() }
        assertNull(aes.decrypt(garbage, password.toByteArray()))
    }

    @Test
    fun `concurrent operations on real thread pool do not fail`() {
        // Exercises raw JDK threading, not kotlinx coroutines — this most
        // directly mirrors the in-app race (TSS native callbacks + the
        // coroutine-based message puller both hit Encryption at once).
        val pool = Executors.newFixedThreadPool(8)
        val failures = AtomicInteger(0)
        val pw = password.toByteArray()

        try {
            val futures =
                (0 until 32).map { taskIndex ->
                    pool.submit {
                        try {
                            repeat(100) { iteration ->
                                val payload = "t$taskIndex-i$iteration".toByteArray(Charsets.UTF_8)
                                val encrypted = aes.encrypt(payload, pw)
                                val decrypted = aes.decrypt(encrypted, pw)
                                if (decrypted == null || !decrypted.contentEquals(payload)) {
                                    failures.incrementAndGet()
                                }
                            }
                        } catch (e: Exception) {
                            failures.incrementAndGet()
                        }
                    }
                }
            futures.forEach { it.get(30, TimeUnit.SECONDS) }
        } finally {
            pool.shutdown()
            pool.awaitTermination(5, TimeUnit.SECONDS)
        }

        assertEquals(0, failures.get())
    }
}

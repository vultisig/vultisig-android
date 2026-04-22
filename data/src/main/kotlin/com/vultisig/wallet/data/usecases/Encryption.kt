package com.vultisig.wallet.data.usecases

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.BadPaddingException
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import timber.log.Timber

interface Encryption {
    fun encrypt(data: ByteArray, password: ByteArray): ByteArray

    fun decrypt(data: ByteArray, password: ByteArray): ByteArray?
}

internal class AesEncryption @Inject constructor() : Encryption {

    // SecureRandom is documented as thread-safe; keep as a shared field.
    // Cipher and MessageDigest are not thread-safe and must be created per call —
    // the class is a @Singleton and is hit concurrently from TSS messaging,
    // vault parsing, and keygen/keysign flows.
    private val random = SecureRandom()

    override fun encrypt(data: ByteArray, password: ByteArray): ByteArray {
        val keySpec = SecretKeySpec(sha256(password), ALGORITHM)
        val iv = ByteArray(GCM_IV_LENGTH).also(random::nextBytes)
        val cipher = Cipher.getInstance(GCM_TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
        return iv + cipher.doFinal(data)
    }

    override fun decrypt(data: ByteArray, password: ByteArray): ByteArray? {
        return try {
            val keySpec = SecretKeySpec(sha256(password), ALGORITHM)
            val iv = data.copyOfRange(0, GCM_IV_LENGTH)
            val encryptedData = data.copyOfRange(GCM_IV_LENGTH, data.size)
            val cipher = Cipher.getInstance(GCM_TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, keySpec, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
            cipher.doFinal(encryptedData)
        } catch (e: Exception) {
            Timber.d(e, "GCM decrypt failed, falling back to legacy CBC")
            decryptLegacyCbc(data, password)
        }
    }

    private fun decryptLegacyCbc(data: ByteArray, password: ByteArray): ByteArray? {
        return try {
            val keySpec = SecretKeySpec(sha256(password), ALGORITHM)
            val cipher = Cipher.getInstance(CBC_TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, keySpec, IvParameterSpec(ByteArray(CBC_IV_LENGTH)))
            cipher.doFinal(data)
        } catch (e: BadPaddingException) {
            Timber.e(e, "Failed to decrypt data")
            null
        }
    }

    private fun sha256(input: ByteArray): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(input)

    companion object {
        private const val ALGORITHM = "AES"
        private const val GCM_TRANSFORMATION = "AES/GCM/NoPadding"
        // PKCS5Padding is byte-identical to PKCS7Padding for AES (16-byte block size).
        // Using PKCS5Padding for portability — accepted by both Android and plain JVM.
        private const val CBC_TRANSFORMATION = "AES/CBC/PKCS5Padding"
        private const val GCM_IV_LENGTH = 12
        private const val GCM_TAG_LENGTH_BITS = 128
        private const val CBC_IV_LENGTH = 16
    }
}

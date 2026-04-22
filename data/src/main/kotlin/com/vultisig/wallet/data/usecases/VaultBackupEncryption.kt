package com.vultisig.wallet.data.usecases

import java.nio.ByteBuffer
import java.security.GeneralSecurityException
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import timber.log.Timber

interface VaultBackupEncryption {
    fun encrypt(data: ByteArray, password: ByteArray): ByteArray

    fun decrypt(data: ByteArray, password: ByteArray): ByteArray?
}

internal class Pbkdf2AesEncryption @Inject constructor(private val legacyEncryption: Encryption) :
    VaultBackupEncryption {

    private val random = SecureRandom()

    override fun encrypt(data: ByteArray, password: ByteArray): ByteArray {
        val salt = ByteArray(SALT_LENGTH)
        random.nextBytes(salt)

        val keyBytes = deriveKey(password, salt)
        val keySpec = SecretKeySpec(keyBytes, AES)
        keyBytes.fill(0)

        val iv = ByteArray(IV_LENGTH)
        random.nextBytes(iv)

        val cipher = Cipher.getInstance(AES_GCM_NO_PADDING)
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, GCMParameterSpec(GCM_TAG_BITS, iv))
        val encryptedData = cipher.doFinal(data)

        return PBKDF2_MAGIC + salt + iv + encryptedData
    }

    override fun decrypt(data: ByteArray, password: ByteArray): ByteArray? {
        if (!isPbkdf2(data)) {
            return legacyEncryption.decrypt(data, password)
        }
        if (data.size < PBKDF2_HEADER_SIZE + GCM_TAG_BYTES) {
            Timber.e("Invalid PBKDF2 vault backup payload")
            return null
        }
        return try {
            decryptPbkdf2(data, password)
        } catch (e: GeneralSecurityException) {
            Timber.e(e, "PBKDF2 decryption failed")
            null
        } catch (e: IllegalArgumentException) {
            Timber.e(e, "Invalid PBKDF2 vault backup payload")
            null
        }
    }

    private fun isPbkdf2(data: ByteArray): Boolean {
        if (data.size < PBKDF2_MAGIC_SIZE) return false
        for (i in 0 until PBKDF2_MAGIC_SIZE) {
            if (data[i] != PBKDF2_MAGIC[i]) return false
        }
        return true
    }

    private fun decryptPbkdf2(data: ByteArray, password: ByteArray): ByteArray {
        val salt = data.copyOfRange(PBKDF2_MAGIC_SIZE, PBKDF2_MAGIC_SIZE + SALT_LENGTH)
        val iv = data.copyOfRange(PBKDF2_MAGIC_SIZE + SALT_LENGTH, PBKDF2_HEADER_SIZE)
        val encryptedData = data.copyOfRange(PBKDF2_HEADER_SIZE, data.size)

        val keyBytes = deriveKey(password, salt)
        val keySpec = SecretKeySpec(keyBytes, AES)
        keyBytes.fill(0)

        val cipher = Cipher.getInstance(AES_GCM_NO_PADDING)
        cipher.init(Cipher.DECRYPT_MODE, keySpec, GCMParameterSpec(GCM_TAG_BITS, iv))
        return cipher.doFinal(encryptedData)
    }

    private fun deriveKey(password: ByteArray, salt: ByteArray): ByteArray {
        val charBuffer = Charsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(password))
        val passwordChars = CharArray(charBuffer.remaining())
        charBuffer.get(passwordChars)
        val keySpec = PBEKeySpec(passwordChars, salt, PBKDF2_ITERATIONS, KEY_LENGTH_BITS)
        return try {
            val factory = SecretKeyFactory.getInstance(PBKDF2_ALGORITHM)
            factory.generateSecret(keySpec).encoded
        } finally {
            keySpec.clearPassword()
            passwordChars.fill('\u0000')
            if (charBuffer.hasArray()) {
                charBuffer.array().fill('\u0000')
            }
        }
    }

    companion object {
        // "VLT\x02" — 4-byte magic prefix distinguishable from random GCM IV bytes
        private val PBKDF2_MAGIC = byteArrayOf(0x56, 0x4C, 0x54, 0x02)
        private const val PBKDF2_MAGIC_SIZE = 4
        private const val SALT_LENGTH = 16
        private const val IV_LENGTH = 12
        private const val GCM_TAG_BITS = 128
        private const val GCM_TAG_BYTES = GCM_TAG_BITS / 8
        private const val KEY_LENGTH_BITS = 256
        private const val PBKDF2_ITERATIONS = 600_000
        private const val PBKDF2_ALGORITHM = "PBKDF2WithHmacSHA256"
        private const val AES = "AES"
        private const val AES_GCM_NO_PADDING = "AES/GCM/NoPadding"

        private const val PBKDF2_HEADER_SIZE =
            PBKDF2_MAGIC_SIZE + SALT_LENGTH + IV_LENGTH // 32 bytes
    }
}

package com.vultisig.wallet.data.usecases

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

        val iv = ByteArray(IV_LENGTH)
        random.nextBytes(iv)

        val cipher = Cipher.getInstance(AES_GCM_NO_PADDING)
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, GCMParameterSpec(GCM_TAG_BITS, iv))
        val encryptedData = cipher.doFinal(data)

        return byteArrayOf(VERSION_PBKDF2) + salt + iv + encryptedData
    }

    override fun decrypt(data: ByteArray, password: ByteArray): ByteArray? {
        if (data.isEmpty() || data[0] != VERSION_PBKDF2) {
            return legacyEncryption.decrypt(data, password)
        }
        if (data.size < PBKDF2_HEADER_SIZE + GCM_TAG_BYTES) {
            Timber.e("Invalid PBKDF2 vault backup payload")
            return null
        }
        return try {
            decryptPbkdf2(data, password)
        } catch (e: Exception) {
            Timber.e(e, "PBKDF2 decryption failed")
            null
        }
    }

    private fun decryptPbkdf2(data: ByteArray, password: ByteArray): ByteArray {
        val salt = data.copyOfRange(1, 1 + SALT_LENGTH)
        val iv = data.copyOfRange(1 + SALT_LENGTH, PBKDF2_HEADER_SIZE)
        val encryptedData = data.copyOfRange(PBKDF2_HEADER_SIZE, data.size)

        val keyBytes = deriveKey(password, salt)
        val keySpec = SecretKeySpec(keyBytes, AES)

        val cipher = Cipher.getInstance(AES_GCM_NO_PADDING)
        cipher.init(Cipher.DECRYPT_MODE, keySpec, GCMParameterSpec(GCM_TAG_BITS, iv))
        return cipher.doFinal(encryptedData)
    }

    private fun deriveKey(password: ByteArray, salt: ByteArray): ByteArray {
        val keySpec =
            PBEKeySpec(
                String(password, Charsets.UTF_8).toCharArray(),
                salt,
                PBKDF2_ITERATIONS,
                KEY_LENGTH_BITS,
            )
        val factory = SecretKeyFactory.getInstance(PBKDF2_ALGORITHM)
        return factory.generateSecret(keySpec).encoded
    }

    companion object {
        private const val VERSION_PBKDF2: Byte = 0x02
        private const val SALT_LENGTH = 16
        private const val IV_LENGTH = 12
        private const val GCM_TAG_BITS = 128
        private const val GCM_TAG_BYTES = GCM_TAG_BITS / 8
        private const val KEY_LENGTH_BITS = 256
        private const val PBKDF2_ITERATIONS = 600_000
        private const val PBKDF2_ALGORITHM = "PBKDF2WithHmacSHA256"
        private const val AES = "AES"
        private const val AES_GCM_NO_PADDING = "AES/GCM/NoPadding"

        private const val PBKDF2_HEADER_SIZE = 1 + SALT_LENGTH + IV_LENGTH // 29 bytes
    }
}

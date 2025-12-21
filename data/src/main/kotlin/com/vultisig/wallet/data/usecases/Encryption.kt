package com.vultisig.wallet.data.usecases

import timber.log.Timber
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.BadPaddingException
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException


interface Encryption {
    fun encrypt(data: ByteArray, password: ByteArray): ByteArray
    fun decrypt(data: ByteArray, password: ByteArray): ByteArray?
}

internal class AesEncryption @Inject constructor() : Encryption {

    private val cipher = Cipher.getInstance("AES/GCM/NoPadding")
    private val messageDigest = MessageDigest.getInstance("SHA-256")
    private val algorithm = "AES"
    private val random = SecureRandom()

    override fun encrypt(data: ByteArray, password: ByteArray): ByteArray {
        val keyBytes = messageDigest.digest(password)
        val keySpec = SecretKeySpec(
            keyBytes,
            algorithm
        )
        val iv = ByteArray(12)
        random.nextBytes(iv)
        val parameterSpec = GCMParameterSpec(
            128,
            iv
        )
        cipher.init(
            Cipher.ENCRYPT_MODE,
            keySpec,
            parameterSpec
        )
        val encryptedData = cipher.doFinal(data)
        val combined = iv + encryptedData
        return combined
    }

    override fun decrypt(data: ByteArray, password: ByteArray): ByteArray? {
        try {
            val keyBytes = messageDigest.digest(password)
            val keySpec = SecretKeySpec(
                keyBytes,
                algorithm
            )
            val iv = data.copyOfRange(
                0,
                12
            )
            val encryptedData = data.copyOfRange(
                12,
                data.size
            )
            cipher.init(
                Cipher.DECRYPT_MODE,
                keySpec,
                GCMParameterSpec(
                    128,
                    iv
                )
            )
            return cipher.doFinal(encryptedData)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Timber.e(
                e,
                "switch to old version decryption"
            )
            return decryptOldVersion(
                data = data,
                password = password
            )
        }
    }


    private fun decryptOldVersion(data: ByteArray, password: ByteArray): ByteArray? {
        try {
            val oldCipher = Cipher.getInstance("AES/CBC/PKCS7PADDING")
            oldCipher.init(
                Cipher.DECRYPT_MODE,
                generateKey(password),
                IvParameterSpec(ByteArray(16))
            )
            val cipherText = oldCipher.doFinal(data)
            return cipherText
        } catch (e: BadPaddingException) {
            Timber.e(
                e,
                "Failed to decrypt data"
            )
            return null
        }
    }

    private fun generateKey(password: ByteArray): SecretKeySpec {
        val digest: MessageDigest = MessageDigest.getInstance("SHA-256")
        val bytes = password
        digest.update(
            bytes,
            0,
            bytes.size
        )
        val key = digest.digest()
        val secretKeySpec = SecretKeySpec(
            key,
            "AES"
        )
        return secretKeySpec
    }

}
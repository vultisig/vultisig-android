package com.vultisig.wallet.common

import android.util.Base64
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.security.MessageDigest
import javax.crypto.BadPaddingException
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject


@Module
@InstallIn(SingletonComponent::class)
internal interface CryptoModule {
    @Binds
    fun bindCryptoManager(aesCryptoManager: AESCryptoManager): CryptoManager
}


internal interface CryptoManager {
    fun encrypt(strToEncrypt: String, key: String): String
    fun decrypt(key: String, dataToDecrypt: ByteArray): String?
}


internal class AESCryptoManager @Inject constructor() : CryptoManager {

    private val cipher = Cipher.getInstance("AES/CBC/PKCS7PADDING")
    private val ivParameterSpec = IvParameterSpec(ByteArray(16))

    override fun encrypt(strToEncrypt: String, key: String): String {
        val plainText = strToEncrypt.toByteArray(Charsets.UTF_8)
        cipher.init(Cipher.ENCRYPT_MODE, generateKey(key), ivParameterSpec)
        val cipherText = cipher.doFinal(plainText)
        return Base64.encodeToString(cipherText, Base64.DEFAULT)
    }

    override fun decrypt(key: String, dataToDecrypt: ByteArray): String? {
        try {
            val textToDecrypt = Base64.decode(dataToDecrypt, Base64.DEFAULT)
            cipher.init(Cipher.DECRYPT_MODE, generateKey(key), ivParameterSpec)
            val cipherText = cipher.doFinal(textToDecrypt)
            return cipherText.buildString()
        } catch (e: BadPaddingException) {
            return null
        }
    }

    private fun generateKey(password: String): SecretKeySpec {
        val digest: MessageDigest = MessageDigest.getInstance("SHA-256")
        val bytes = password.toByteArray()
        digest.update(bytes, 0, bytes.size)
        val key = digest.digest()
        val secretKeySpec = SecretKeySpec(key, "AES")
        return secretKeySpec
    }

}
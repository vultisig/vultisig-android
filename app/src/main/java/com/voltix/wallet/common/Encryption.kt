package com.voltix.wallet.common

import okio.ByteString.Companion.decodeBase64
import okio.ByteString.Companion.decodeHex
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi


@OptIn(ExperimentalEncodingApi::class)
fun String.Encrypt(key: String): String {
    val decodeKey = key.decodeHex()
    val cipher = Cipher.getInstance("AES/CBC/PKCS7Padding")
    val secureRandom = SecureRandom()
    val iv = ByteArray(cipher.blockSize).apply { secureRandom.nextBytes(this) }
    val secretKeySpec = SecretKeySpec(decodeKey.toByteArray(), "AES")
    val ivParameterSpec = IvParameterSpec(iv)
    cipher.init(Cipher.ENCRYPT_MODE, secretKeySpec, ivParameterSpec)
    val encrypted = cipher.doFinal(this.toByteArray())
    return Base64.encode(iv + encrypted)
}

fun String.Decrypt(key: String): String {
    val decodeKey = key.decodeHex()
    val decodedRaw = this.decodeBase64()
    decodedRaw?.let {
    val cipher = Cipher.getInstance("AES/CBC/PKCS7Padding")
        val iv  = it.substring(0, cipher.blockSize)
        val encrypted = it.substring(cipher.blockSize)
        val secretKeySpec = SecretKeySpec(decodeKey.toByteArray(), "AES")
        val ivParameterSpec = IvParameterSpec(iv.toByteArray())
        cipher.init(Cipher.DECRYPT_MODE, secretKeySpec, ivParameterSpec)
        return cipher.doFinal(encrypted.toByteArray()).toString(Charsets.UTF_8)
    }
    // if failed , then return the original string
    return this
}

fun String.md5(): String {
    val bytes = this.toByteArray()
    val md = java.security.MessageDigest.getInstance("MD5")
    val digest = md.digest(bytes)
    return digest.joinToString("") { "%02x".format(it) }
}
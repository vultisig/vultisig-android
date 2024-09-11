package com.vultisig.wallet.data.common

import okio.ByteString.Companion.decodeBase64
import okio.ByteString.Companion.decodeHex
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi


@OptIn(ExperimentalEncodingApi::class)
fun String.encrypt(key: String): String {
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

fun String.decrypt(key: String): String {
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
    val md = MessageDigest.getInstance("MD5")
    val digest = md.digest(bytes)
    return digest.joinToString("") { "%02x".format(it) }
}

fun String.sha256(): String {
    try {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(this.toByteArray(Charsets.UTF_8))
        val hexString = StringBuilder()
        for (i in hash.indices) {
            val hex = Integer.toHexString(0xff and hash[i].toInt())
            if (hex.length == 1) hexString.append('0')
            hexString.append(hex)
        }
        return hexString.toString()
    } catch (ex: Exception) {
        throw RuntimeException(ex)
    }
}
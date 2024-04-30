package com.voltix.wallet.common

import java.security.SecureRandom

object Utils {
    val deviceName: String
        get() {
            return "${android.os.Build.MODEL}-${(100..999).random()}"
        }
    val encryptionKeyHex: String
        get() {
            val secureRandom = SecureRandom()
            val keyBytes = ByteArray(32) // 256 bits for AES-256 encryption key
            secureRandom.nextBytes(keyBytes)
            return keyBytes.joinToString("") { "%02x".format(it) }
        }

    fun getThreshold(input: Int):Int{
        return Math.ceil(input * 2.0/ 3.0).toInt()
    }
}
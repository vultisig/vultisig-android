package com.vultisig.wallet.common

import java.security.SecureRandom
import kotlin.math.ceil

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
        return ceil(input * 2.0/ 3.0).toInt()
    }
}
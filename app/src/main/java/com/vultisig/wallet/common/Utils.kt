package com.vultisig.wallet.common

import android.content.Context
import android.provider.Settings
import org.bouncycastle.crypto.digests.Blake2bDigest
import java.security.SecureRandom
import kotlin.math.ceil

object Utils {
    fun deviceName(context: Context): String {
        val identifierForVendor: String? = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ANDROID_ID
        )
        return "${android.os.Build.MODEL}-${identifierForVendor?.takeLast(3) ?: "N/A"}"
    }

    val encryptionKeyHex: String
        get() {
            val secureRandom = SecureRandom()
            val keyBytes = ByteArray(32) // 256 bits for AES-256 encryption key
            secureRandom.nextBytes(keyBytes)
            return keyBytes.joinToString("") { "%02x".format(it) }
        }

    fun getThreshold(input: Int): Int {
        return ceil(input * 2.0 / 3.0).toInt()
    }

    fun blake2bHash(input: ByteArray): ByteArray {
        val digest = Blake2bDigest(256)
        digest.update(input, 0, input.size)
        val output = ByteArray(digest.digestSize)
        digest.doFinal(output, 0)
        return output
    }
}
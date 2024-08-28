package com.vultisig.wallet.common

import android.util.Base64
import io.ktor.util.encodeBase64
import org.junit.Test

internal class CryptoManagerTest {

    private val cryptoManager = AESCryptoManager()

    @Test
    fun encrypt() {
        val plainText = "aBcDe12345"
        val password = "12345"
        val encrypt = cryptoManager.encrypt(
            plainText.encodeToByteArray(),
            password
        ) ?: ByteArray(0)
        assert(encrypt.encodeBase64().isNotEmpty())
    }

    @Test
    fun decrypt() {
        val encText = "/UAOqTL07tgTIhe+UJlCDEIj9l7WxaU1gAN01UP3zMejmyvY0zI="
        val password = "12345"
        val decText = "aBcDe12345"
        val assertedDec = cryptoManager.decrypt(
            Base64.decode(
                encText,
                Base64.DEFAULT
            ),
            password
        )?.decodeToString() ?: ""
        assert(assertedDec == decText)
    }
}
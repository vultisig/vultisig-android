package com.vultisig.wallet.common

import org.junit.Test

internal class CryptoManagerTest {

    private val cryptoManager = AESCryptoManager()

    @Test
    fun encrypt() {
        val plainText = "aBcDe12345"
        val password = "12345"
        val encrypt = cryptoManager.encrypt(plainText, password) ?: ""
        assert(encrypt.isNotEmpty())
    }

    @Test
    fun decrypt() {
        val encText = "/UAOqTL07tgTIhe+UJlCDEIj9l7WxaU1gAN01UP3zMejmyvY0zI="
        val password = "12345"
        val decText = "aBcDe12345"
        val assertedDec = cryptoManager.decrypt(encText, password) ?: ""
        assert(assertedDec == decText)
    }
}



package com.vultisig.wallet.common

import android.util.Log
import org.junit.Test

class EncryptionTest{
    @Test
    fun testEncryption(){
        val encryptionKey = Utils.encryptionKeyHex
        Log.d("EncryptionTest", "encryptionKey: $encryptionKey")
        val encrypted ="helloworld".Encrypt(encryptionKey)
        val decrypted = encrypted.Decrypt(encryptionKey)
        assert(decrypted == "helloworld")
    }
    @Test
    fun testEnsureDecryptIOS(){
        val encryptionKey = "2c457ded43eb6b15611c9452a7671b02c11405f810924ae6373663e2ad96ebd4"
        val encrypted = "dnHGOuIZ89i5UKanha9vUzCdUBNVony3wqJnf0SYLBA="
        val decrypted = encrypted.Decrypt(encryptionKey)
        assert(decrypted == "helloworld")
    }
    @Test
    fun testMD5(){
        val md5 = "helloworld".md5()
        assert(md5 == "fc5e038d38a57032085441e7fe7010b0")
    }
}
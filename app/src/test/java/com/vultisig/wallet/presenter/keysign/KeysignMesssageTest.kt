package com.vultisig.wallet.presenter.keysign


import com.vultisig.wallet.common.Utils
import com.vultisig.wallet.data.DataModule
import com.vultisig.wallet.models.Coins
import org.junit.Assert
import org.junit.Test
import java.math.BigInteger
import java.util.UUID

class KeysignMesssageTest {
    @Test
    fun testToJson() {
        val keysignMesssage = KeysignMesssage(
            sessionID = UUID.randomUUID().toString(),
            serviceName = "serviceName",
            payload = KeysignPayload(
                coin = Coins.SupportedCoins.filter { it.ticker == "RUNE" }.first(),
                toAddress = "thor1x6f63myfwktevd6mkspdeus9rea5a72w6ynax2",
                toAmount = BigInteger("10000000"), // 0.1 RUNE
                blockChainSpecific = BlockChainSpecific.THORChain(
                    accountNumber = BigInteger("1024"),
                    sequence = BigInteger("0")
                ),
                vaultPublicKeyECDSA = "asdfasdf",
            ), encryptionKeyHex = Utils.encryptionKeyHex,
            usevultisigRelay = true
        )
        val t = BlockChainSpecific.THORChain(
            accountNumber = BigInteger("1024"),
            sequence = BigInteger("0")
        )
        val gson = DataModule.provideGson()
        val json = gson.toJson(keysignMesssage)

        val result = gson.fromJson(json, KeysignMesssage::class.java)
        val input = """
            {"toAddress":"thor1x6f63myfwktevd6mkspdeus9rea5a72w6ynax2","utxos":[],"toAmount":["+",10000000],"vaultPubKeyECDSA":"asdfasdf","chainSpecific":{"THORChain":{"sequence":0,"accountNumber":1024}},"coin":{"isNativeToken":true,"priceRate":0,"feeUnit":"Rune","chainType":{"THORChain":{}},"ticker":"RUNE","decimals":"8","logo":"rune","address":"","hexPublicKey":"","rawBalance":"0","feeDefault":"0.02","priceProviderId":"thorchain","contractAddress":"","chain":"thorChain"}}"""
        val result1 = gson.fromJson(input, KeysignPayload::class.java)
        Assert.assertEquals(result1.toAddress, "thor1x6f63myfwktevd6mkspdeus9rea5a72w6ynax2")
        Assert.assertEquals(result1.toAmount, BigInteger("10000000"))
        print(gson.toJson(result1))
    }
}
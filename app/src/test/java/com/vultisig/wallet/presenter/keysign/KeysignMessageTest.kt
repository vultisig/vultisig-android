package com.vultisig.wallet.presenter.keysign


import com.vultisig.wallet.common.Utils
import com.vultisig.wallet.data.DataModule
import com.vultisig.wallet.models.Coins
import org.junit.Assert
import org.junit.Test
import java.math.BigInteger
import java.util.UUID

class KeysignMessageTest {
    @Test
    fun testToJson() {
        val keysignMessage = KeysignMessage(
            sessionID = UUID.randomUUID().toString(),
            serviceName = "serviceName",
            payload = KeysignPayload(
                coin = Coins.SupportedCoins.first { it.ticker == "RUNE" },
                toAddress = "thor1x6f63myfwktevd6mkspdeus9rea5a72w6ynax2",
                toAmount = BigInteger("10000000"), // 0.1 RUNE
                blockChainSpecific = BlockChainSpecific.THORChain(
                    accountNumber = BigInteger("1024"),
                    sequence = BigInteger("0"),
                    fee = BigInteger("2000000")
                ),
                vaultPublicKeyECDSA = "asdfasdf",
                vaultLocalPartyID = "asdfasdf"
            ), encryptionKeyHex = Utils.encryptionKeyHex,
            useVultisigRelay = true
        )
        val t = BlockChainSpecific.THORChain(
            accountNumber = BigInteger("1024"),
            sequence = BigInteger("0"),
            fee = BigInteger("2000000")
        )
        val gson = DataModule.provideGson()
        val json = gson.toJson(keysignMessage)
        Assert.assertEquals(true, gson.toJson(t).contains("THORChain"))
        val result = gson.fromJson(json, KeysignMessage::class.java)
        Assert.assertEquals(keysignMessage.sessionID, result.sessionID)
        Assert.assertEquals(keysignMessage.serviceName, result.serviceName)
        Assert.assertEquals(
            true,
            (result.payload.blockChainSpecific as? BlockChainSpecific.THORChain) != null
        )
        val input = """
            {"toAddress":"thor1x6f63myfwktevd6mkspdeus9rea5a72w6ynax2","utxos":[],"toAmount":["+",10000000],"vaultPubKeyECDSA":"asdfasdf","vaultLocalPartyID":"asdfasdf","chainSpecific":{"THORChain":{"sequence":0,"accountNumber":1024,"fee":"2000000"}},"coin":{"isNativeToken":true,"priceRate":0,"feeUnit":"Rune","chainType":{"THORChain":{}},"ticker":"RUNE","decimals":"8","logo":"rune","address":"","hexPublicKey":"","rawBalance":"0","feeDefault":"0.02","priceProviderId":"thorchain","contractAddress":"","chain":"thorChain"}}"""
        val result1 = gson.fromJson(input, KeysignPayload::class.java)
        Assert.assertEquals(result1.toAddress, "thor1x6f63myfwktevd6mkspdeus9rea5a72w6ynax2")
        Assert.assertEquals(result1.toAmount, BigInteger("10000000"))
        print(gson.toJson(result1))
    }

}
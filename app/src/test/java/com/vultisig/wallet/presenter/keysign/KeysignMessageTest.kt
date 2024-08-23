package com.vultisig.wallet.presenter.keysign


import com.vultisig.wallet.common.Utils
import com.vultisig.wallet.data.DataModule
import com.vultisig.wallet.models.Coins
import org.hamcrest.CoreMatchers.*
import org.hamcrest.MatcherAssert.*
import org.junit.Assert
import org.junit.jupiter.api.Test
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
        assertThat(
            gson.toJson(t).contains("THORChain"),
            `is`(true)
        )
        val result = gson.fromJson(json, KeysignMessage::class.java)
        assertThat(
            result.sessionID,
            `is`(keysignMessage.sessionID)
        )
        assertThat(
            result.serviceName,
            `is`(keysignMessage.serviceName)
        )
        assertThat(
            (result.payload.blockChainSpecific as? BlockChainSpecific.THORChain) != null,
            `is`(true)
        )
        val input = """
            {"toAddress":"thor1x6f63myfwktevd6mkspdeus9rea5a72w6ynax2","utxos":[],"toAmount":["+",10000000],"vaultPubKeyECDSA":"asdfasdf","vaultLocalPartyID":"asdfasdf","chainSpecific":{"THORChain":{"sequence":0,"accountNumber":1024,"fee":"2000000"}},"coin":{"isNativeToken":true,"priceRate":0,"feeUnit":"Rune","chainType":{"THORChain":{}},"ticker":"RUNE","decimals":"8","logo":"rune","address":"","hexPublicKey":"","rawBalance":"0","feeDefault":"0.02","priceProviderId":"thorchain","contractAddress":"","chain":"thorChain"}}"""
        val result1 = gson.fromJson(input, KeysignPayload::class.java)
        assertThat(
            "thor1x6f63myfwktevd6mkspdeus9rea5a72w6ynax2",
            `is`(result1.toAddress)
        )
        assertThat(
            BigInteger("10000000"),
            `is`(result1.toAmount)
        )
        print(gson.toJson(result1))
    }

}
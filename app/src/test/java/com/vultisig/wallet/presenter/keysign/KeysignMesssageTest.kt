package com.vultisig.wallet.presenter.keysign


import com.vultisig.wallet.common.Utils
import com.vultisig.wallet.common.unzipZlib
import com.vultisig.wallet.data.DataModule
import com.vultisig.wallet.models.Coins
import io.ktor.util.decodeBase64Bytes
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
        val json = gson.toJson(keysignMesssage)
        Assert.assertEquals(true, gson.toJson(t).contains("THORChain"))
        val result = gson.fromJson(json, KeysignMesssage::class.java)
        Assert.assertEquals(keysignMesssage.sessionID, result.sessionID)
        Assert.assertEquals(keysignMesssage.serviceName, result.serviceName)
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

    @Test
    fun testDecode() {
        val input =
            """XVLLbtswEPwXXmsHFEVSlG9+AQlauIaT9BL0sCKXtqxn9HDsBPn3rmSnQMsTubuzOzPcD1bDJa/AsdkHO0Gfdz8qC/kWmu7ysGIzlm4PVYnTxTJmE2artBwK6ya1uG2qU+qweSAs6w5VYw9A6aGq7Bqw3dy5BtuWshQcITvokM30nQknrEtthg0ld8+bNRV4xOcy7YZAXyIFHNq0gHzAG3rm1b6ia3NNjrOeLjUOdJ7uf+6W4/DZx+fnhMHfwQOtAI3mWf2a1ecjcYzjCHPjM1+EZXkEya2Ub6rPvpreUMubmAbeFpBDaWkSE/x6KJ62G+jSEz5VGRKma3ocNazQDy5SMb/jggoPeN72SZ7a73gZomEYCDpGOy+dVtxpL0IrrdKgwQbGJLFHGwmlAu6RxyLGxHMJXkQSkfOYkcICi+rqa1fN/1VLtSaKjr4oRHsuzpfX7BgW8bmDzByQ7/fRpe7f36/IoupLovrCvrFJcNP2++bDY03++9T+bzADawfYpi+S4f8CrnmgJ6zF1x5Hm/jwB313rojTC7Ub14o8IAPWy9XjfHQBNddaBQYSE3nrrFcIYSQkJCGi1jGiwSQSOiJDnOFSGVAR+e8jGue8GVxoSXZaleOeLuOVWavFcmpWUTCVOg6mc7Okp9E6WAfxYhVLNkCaEy3iBorhP38RsbRN99NARZQk+s2l7qglUb3H87B5KKR3IrYycS70OgxDNJ4rEQOqkAuuSDw4kSiXoEMwKhQahJRGaBkpoK59i19zdpgDLYGnrcbPPw=="""
        val inputBytes = input.decodeBase64Bytes()
        val result = inputBytes.unzipZlib()
    }
}
package com.vultisig.wallet.data.api

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.vultisig.wallet.data.api.models.BlockChairInfo
import com.vultisig.wallet.data.api.models.BlockChairInfoJson
import com.vultisig.wallet.data.api.models.SuggestedTransactionFeeDataJson
import com.vultisig.wallet.data.api.models.TransactionHashDataJson
import com.vultisig.wallet.data.api.models.TransactionHashRequestBodyJson
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jeasy.random.EasyRandom
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test


internal class BlockChairApiImpTest {
    private val gson = Gson()
    private val easyRandom = EasyRandom()
    private val json = Json {
        ignoreUnknownKeys = true
    }

    @Test
    fun `check gson to kotlinx in getAddressInfo`() {
        //given
        val fakeAddress = easyRandom.nextObject(String::class.java)
        val blockChairInfoMock = easyRandom.nextObject(BlockChairInfo::class.java)
        val responseData =
            """
           {          
              "data" : {
                 "$fakeAddress": ${json.encodeToString(blockChairInfoMock)}          
               }
           }
        """.trimIndent()

        //when
        val rootObject = gson.fromJson(responseData, JsonObject::class.java)
        val data = rootObject.getAsJsonObject("data").getAsJsonObject().get(fakeAddress)
        val parsedWithGson = gson.fromJson(data, BlockChairInfo::class.java)
        val parsedWithKotlinX = json.decodeFromString<BlockChairInfoJson>(responseData)
        // checking their balance due their common serialization name
        val gsonBalance = parsedWithGson.address.balance
        val kotlinXBalance = parsedWithKotlinX.data[fakeAddress]!!.address.balance

        //then
        assertEquals(gsonBalance, kotlinXBalance)
    }

    @Test
    fun `check gson to kotlinx in getBlockChairStats`() {
        //given
        val response = """
            {
                "data" : {
                    "suggested_transaction_fee_per_byte_sat" : 1000
                }
            }
        """.trimIndent()
        val rootObject = gson.fromJson(response, JsonObject::class.java)
        val parsedWithGson = rootObject.getAsJsonObject("data")
            .get("suggested_transaction_fee_per_byte_sat").asBigInteger

        //when
        val parsedWithKotlinX = json
            .decodeFromString<SuggestedTransactionFeeDataJson>(response)
            .data.value

        //then
        assertEquals(parsedWithGson, parsedWithKotlinX)

    }

    @Test
    fun `check gson to kotlinx in broadcastTransaction`() {
        //given
        val response = """
            {
                "data" : {
                    "transaction_hash" : "fake_transaction_hash"
                }
            }
        """.trimIndent()
        val rootObject = gson.fromJson(response, JsonObject::class.java)
        val parsedWithGson = rootObject
            .getAsJsonObject("data").get("transaction_hash").asString

        //when
        val parsedWithKotlinX = json
            .decodeFromString<TransactionHashDataJson>(response)
            .data.value

        //then
        assertEquals(parsedWithGson, parsedWithKotlinX)
    }


    @Test
    fun `check gson to kotlinx in broadcastTransactionRequest`() {
        //given
        val jsonObject = JsonObject()
        val fakeTransaction = "signedTransaction"
        jsonObject.addProperty("data", fakeTransaction)
        val gsonBodyContent = gson.toJson(jsonObject)

        //when
        val kotlinXBodyContent =
            json.encodeToString(TransactionHashRequestBodyJson(fakeTransaction))

        //then
        assertEquals(gsonBodyContent, kotlinXBodyContent)
    }
}
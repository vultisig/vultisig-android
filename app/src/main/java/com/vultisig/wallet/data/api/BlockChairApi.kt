package com.vultisig.wallet.data.api

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.vultisig.wallet.data.models.BlockchairInfo
import com.vultisig.wallet.models.Chain
import com.vultisig.wallet.models.Coin
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import timber.log.Timber
import java.math.BigInteger
import javax.inject.Inject

internal interface BlockChairApi {
    suspend fun getAddressInfo(
        chain: Chain,
        address: String,
    ): BlockchairInfo?

    suspend fun getBlockchairStats(chain: Chain): BigInteger
    suspend fun broadcastTransaction(coin: Coin, signedTransaction: String): String
}

internal class BlockChairApiImp @Inject constructor(
    private val gson: Gson,
    private val httpClient: HttpClient,
) : BlockChairApi {

    private fun getChainName(chain: Chain): String = when (chain) {
        Chain.Bitcoin -> "bitcoin"
        Chain.BitcoinCash -> "bitcoin-cash"
        Chain.Litecoin -> "litecoin"
        Chain.Dogecoin -> "dogecoin"
        Chain.Dash -> "dash"
        else -> throw IllegalArgumentException("Unsupported chain $chain")
    }

    private fun getChainName(coin: Coin): String = getChainName(coin.chain)

    override suspend fun getAddressInfo(
        chain: Chain,
        address: String,
    ): BlockchairInfo? {
        try {
            val response =
                httpClient.get("https://api.vultisig.com/blockchair/${getChainName(chain)}/dashboards/address/${address}?state=latest") {
                    header("Content-Type", "application/json")
                }
            val responseData = response.bodyAsText()
            Timber.d("response data: $responseData")
            val rootObject = gson.fromJson(responseData, JsonObject::class.java)
            val data = rootObject.getAsJsonObject("data").getAsJsonObject().get(address)
            val blockchairInfo = gson.fromJson(data, BlockchairInfo::class.java)
            return blockchairInfo
        } catch (e: Exception) {
            Timber.e("fail to get address info from blockchair: ${e.message}")
        }
        return null
    }

    override suspend fun getBlockchairStats(chain: Chain): BigInteger {
        val response =
            httpClient.get("https://api.vultisig.com/blockchair/${getChainName(chain)}/stats") {
                header("Content-Type", "application/json")
            }
        val rootObject = gson.fromJson(response.bodyAsText(), JsonObject::class.java)
        return rootObject.getAsJsonObject("data")
            .get("suggested_transaction_fee_per_byte_sat").asBigInteger
    }

    override suspend fun broadcastTransaction(coin: Coin, signedTransaction: String): String {
        val jsonObject = JsonObject()
        jsonObject.addProperty("data", signedTransaction)
        val bodyContent = gson.toJson(jsonObject)
        Timber.d("bodyContent:$bodyContent")
        val response =
            httpClient.post("https://api.vultisig.com/blockchair/${getChainName(coin)}/push/transaction") {
                header("Content-Type", "application/json")
                setBody(bodyContent)
            }
        if (response.status != HttpStatusCode.OK) {
            Timber.d("fail to broadcast transaction: ${response.bodyAsText()}")
            throw Exception("fail to broadcast transaction")
        }
        val rootObject = gson.fromJson(response.bodyAsText(), JsonObject::class.java)
        return rootObject.getAsJsonObject("data").get("transaction_hash").asString
    }
}
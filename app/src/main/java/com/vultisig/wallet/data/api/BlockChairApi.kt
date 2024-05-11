package com.vultisig.wallet.data.api

import com.google.common.cache.Cache
import com.google.common.cache.CacheBuilder
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.vultisig.wallet.data.models.BlockchairInfo
import com.vultisig.wallet.models.Chain
import com.vultisig.wallet.models.Coin
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import timber.log.Timber
import java.math.BigInteger
import javax.inject.Inject

internal interface BlockChairApi {
    suspend fun getAddressInfo(coin: Coin): BlockchairInfo?
    suspend fun getBlockchairStats(coin: Coin): BigInteger
}

internal class BlockChairApiImp @Inject constructor(
    private val gson: Gson,
    private val httpClient: HttpClient,
) : BlockChairApi {
    private val cache: Cache<String, BlockchairInfo> = CacheBuilder
        .newBuilder()
        .maximumSize(100)
        .expireAfterWrite(5, java.util.concurrent.TimeUnit.MINUTES)
        .build()

    private fun getChainName(coin: Coin): String {
        return when (coin.chain) {
            Chain.bitcoin -> "bitcoin"
            Chain.bitcoinCash -> "bitcoin-cash"
            Chain.litecoin -> "litecoin"
            Chain.dogecoin -> "dogecoin"
            Chain.dash -> "dash"
            else -> throw IllegalArgumentException("Unsupported chain ${coin.chain}")
        }

    }

    override suspend fun getAddressInfo(coin: Coin): BlockchairInfo? {
        try {
            cache.getIfPresent(coin.address)?.let {
                return it
            }
            val response =
                httpClient.get("https://api.voltix.org/blockchair/${getChainName(coin)}/dashboards/address/${coin.address}") {
                    header("Content-Type", "application/json")
                }
            val rootObject = gson.fromJson(response.bodyAsText(), JsonObject::class.java)
            val data = rootObject.getAsJsonObject("data").getAsJsonObject().get(coin.address)
            val blockchairInfo = gson.fromJson(data, BlockchairInfo::class.java)
            cache.put(coin.address, blockchairInfo)
            return blockchairInfo
        } catch (e: Exception) {
            Timber.e("fail to get address info from blockchair: ${e.message}")
        }
        return null
    }

    override suspend fun getBlockchairStats(coin: Coin): BigInteger {
        val response =
            httpClient.get("https://api.voltix.org/blockchair/${getChainName(coin)}/stats") {
                header("Content-Type", "application/json")
            }
        val rootObject = gson.fromJson(response.bodyAsText(), JsonObject::class.java)
        return rootObject.getAsJsonObject("data")
            .get("suggested_transaction_fee_per_byte_sat").asBigInteger
    }
}
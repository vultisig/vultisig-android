package com.vultisig.wallet.service

import android.util.Log
import com.google.common.cache.Cache
import com.google.common.cache.CacheBuilder
import com.google.gson.Gson
import com.vultisig.wallet.common.Endpoints
import com.vultisig.wallet.common.SettingsCurrency
import com.vultisig.wallet.data.common.data_store.AppDataStore
import com.vultisig.wallet.models.Coin
import com.vultisig.wallet.models.Coins
import com.vultisig.wallet.models.CryptoPrice
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.URL
import java.util.Date
import javax.inject.Inject

class CryptoPriceService @Inject constructor(
    private val appDataStore: AppDataStore
) {
    private val cache: Cache<String, Pair<CryptoPrice, Date>> =
        CacheBuilder
            .newBuilder()
            .build()

    suspend fun getPrice(priceProviderId: String): Double {
        var price = 0.0

        withContext(Dispatchers.IO) {
            val priceCoinGecko = async { getAllCryptoPricesCoinGecko() }.await()

            if (priceCoinGecko != null) {
                price = priceCoinGecko
                    .prices[priceProviderId]
                    ?.get(SettingsCurrency.getCurrency(appDataStore).name) ?: 0.0
            }
        }

        return price
    }

    private suspend fun getAllCryptoPricesCoinGecko(): CryptoPrice? {
        val coins = getCoins().joinToString(separator = ",")
        return fetchAllCryptoPricesCoinGecko(coins, "USD")
    }

    private suspend fun fetchAllCryptoPricesCoinGecko(
        coin: String = "bitcoin",
        fiat: String = "usd"
    ): CryptoPrice? {
        val cacheKey = "$coin-$fiat"

        if (isCacheValid(cacheKey)) {
            return cache.getIfPresent(cacheKey)?.first
        }

        return try {
            val response = fetchPrices(coin, fiat)
            val decodedData = Gson().fromJson(response.toString(), CryptoPrice::class.java)
            cache.put(cacheKey, Pair(decodedData, Date()))
            decodedData
        } catch (e: Exception) {
            Log.d(ERROR_CRYPTO_SERVICE, "${e.message}")
            null
        }
    }

    private suspend fun fetchPrices(
        coin: String,
        fiat: String
    ): StringBuilder {
        val response = StringBuilder()

        withContext(Dispatchers.IO) {
            var line: String
            val connection = URL(Endpoints.fetchCryptoPrices(coin, fiat)).openConnection()
            val reader = BufferedReader(InputStreamReader(connection.inputStream))
            while (reader.readLine().also { line = it } != null) {
                response.append(line)
            }
        }

        return response
    }

    private fun getCoins(): List<Coin> {
        return Coins.SupportedCoins
    }

    private fun isCacheValid(key: String): Boolean {
        val cacheEntry = cache.getIfPresent(key)?.second
        val elapsedTime = Date().time - (cacheEntry?.time ?: 0)
        return elapsedTime <= 300
    }

    private companion object {
        const val ERROR_CRYPTO_SERVICE = "ERROR CRYPTO SERVICE"
    }
}
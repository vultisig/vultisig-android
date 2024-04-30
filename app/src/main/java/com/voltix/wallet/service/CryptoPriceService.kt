package com.voltix.wallet.service

import com.google.gson.Gson
import com.voltix.wallet.common.Endpoints
import com.voltix.wallet.common.SettingsCurrency
import com.voltix.wallet.models.Coin
import com.voltix.wallet.models.Coins
import com.voltix.wallet.models.CryptoPrice
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.URL
import java.util.Date

object CryptoPriceService {
    private val cache: MutableMap<String, Pair<CryptoPrice, Date>> = mutableMapOf()

    suspend fun getPrice(priceProviderId: String): Double {
        var price = 0.0

        withContext(Dispatchers.IO) {
            val priceCoinGecko = async { getAllCryptoPricesCoinGecko() }.await()

            if (priceCoinGecko != null) {
                price = priceCoinGecko
                    .prices[priceProviderId]
                    ?.get(SettingsCurrency.currency.currencyCode) ?: 0.0
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

        if (cache.containsKey(cacheKey) && isCacheValid(cacheKey)) {
            return cache[cacheKey]?.first
        }

        return try {
            val response = fetchPrices(coin, fiat)
            val decodedData = Gson().fromJson(response.toString(), CryptoPrice::class.java)
            cache[cacheKey] = Pair(decodedData, Date())
            decodedData
        } catch (e: Exception) {
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
        val cacheEntry = cache[key]
        val elapsedTime = Date().time - (cacheEntry?.second?.time ?: 0)
        return elapsedTime <= 300
    }
}
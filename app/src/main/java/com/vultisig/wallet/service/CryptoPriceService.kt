package com.vultisig.wallet.service

import android.util.Log
import com.google.common.cache.Cache
import com.google.common.cache.CacheBuilder
import com.google.common.reflect.TypeToken
import com.google.gson.Gson
import com.vultisig.wallet.common.Endpoints
import com.vultisig.wallet.common.SettingsCurrency
import com.vultisig.wallet.data.common.data_store.AppDataStore
import com.vultisig.wallet.models.CryptoPrice
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.lang.reflect.Type
import java.math.BigDecimal
import java.net.URL
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CryptoPriceService @Inject constructor(
    private val appDataStore: AppDataStore,
) {
    private val cache: Cache<String, Pair<CryptoPrice, Date>> =
        CacheBuilder
            .newBuilder()
            .build()

    private var _priceProviderIDs = listOf<String>()
    suspend fun updatePriceProviderIDs(priceProviderIds: List<String>) {
        var needRefresh = false
        if (_priceProviderIDs.containsAll(priceProviderIds).not()) {
            _priceProviderIDs = priceProviderIds
            needRefresh = true
        }
        if (needRefresh) {
            cache.invalidateAll()
        }
    }

    suspend fun getPrice(priceProviderId: String): BigDecimal {
        val currency = SettingsCurrency.getCurrency(appDataStore).name
        cache.getIfPresent(priceProviderId)?.let {
            if ((Date().time - it.second.time) <= 300) {
                // exist in cache and not expired
                return it.first.prices[currency] ?: BigDecimal.ZERO
            }
        }
        // when it get to here , means we need to reload the price from the server
        if (_priceProviderIDs.contains(priceProviderId).not()) {
            _priceProviderIDs = _priceProviderIDs.plus(priceProviderId)
        }
        getAllCryptoPricesCoinGecko()
        return cache.getIfPresent(priceProviderId)?.first?.prices?.get(currency) ?: BigDecimal.ZERO
    }

    private suspend fun getAllCryptoPricesCoinGecko() {
        val priceProviderIds = _priceProviderIDs.joinToString(",")
        val fiats = SettingsCurrency.entries.joinToString(",")
        try {
            val response = fetchPrices(priceProviderIds, fiats)
            val type: Type = object : TypeToken<Map<String, CryptoPrice>>() {}.type
            val decodedData: Map<String, CryptoPrice> = Gson().fromJson(response.toString(), type)
            decodedData.forEach {
                cache.put(it.key, Pair(it.value, Date()))
            }
        } catch (e: Exception) {
            Log.d(ERROR_CRYPTO_SERVICE, "${e.message}")
        }
    }

    private suspend fun fetchPrices(
        coins: String,
        fiats: String,
    ): StringBuilder {
        val response = StringBuilder()

        withContext(Dispatchers.IO) {
            var line: String
            val connection = URL(Endpoints.fetchCryptoPrices(coins, fiats)).openConnection()
            val reader = BufferedReader(InputStreamReader(connection.inputStream))
            while (reader.readLine().also { line = it } != null) {
                response.append(line)
            }
        }

        return response
    }

    private companion object {
        const val ERROR_CRYPTO_SERVICE = "ERROR CRYPTO SERVICE"
    }
}
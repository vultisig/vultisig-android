package com.vultisig.wallet.service

import android.util.Log
import com.google.common.cache.Cache
import com.google.common.cache.CacheBuilder
import com.google.common.reflect.TypeToken
import com.google.gson.Gson
import com.vultisig.wallet.common.Endpoints
import com.vultisig.wallet.common.SettingsCurrency
import com.vultisig.wallet.data.common.data_store.AppDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.lang.reflect.Type
import java.math.BigDecimal
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CryptoPriceService @Inject constructor(
    private val appDataStore: AppDataStore,
    private val gson: Gson,
) {
    private val cache: Cache<String, Pair<Map<String, BigDecimal>, Date>> =
        CacheBuilder
            .newBuilder()
            .build()

    private var _priceProviderIDs = listOf<String>()
    fun updatePriceProviderIDs(priceProviderIds: List<String>) {
        var needRefresh = false
        if (_priceProviderIDs.containsAll(priceProviderIds).not()) {
            _priceProviderIDs = priceProviderIds
            needRefresh = true
        }
        if (needRefresh) {
            cache.invalidateAll()
        }
    }

    suspend fun getSettingCurrency(): SettingsCurrency = SettingsCurrency.getCurrency(appDataStore)
    suspend fun getPrice(priceProviderId: String): BigDecimal {
        val currency = SettingsCurrency.getCurrency(appDataStore).name
        cache.getIfPresent(priceProviderId)?.let {
            if ((Date().time - it.second.time) <= 300 * 1000) {
                // exist in cache and not expired
                return it.first.get(currency.lowercase()) ?: BigDecimal.ZERO
            }
        }
        // when it get to here , means we need to reload the price from the server
        if (_priceProviderIDs.contains(priceProviderId).not()) {
            _priceProviderIDs = _priceProviderIDs.plus(priceProviderId)
        }
        getAllCryptoPricesCoinGecko()
        return cache.getIfPresent(priceProviderId)?.first?.get(currency.lowercase())
            ?: BigDecimal.ZERO
    }

    private suspend fun getAllCryptoPricesCoinGecko() {

        val priceProviderIds = _priceProviderIDs.joinToString(",")
        val fiats = SettingsCurrency.entries.joinToString(",")
        try {
            val response = fetchPrices(priceProviderIds, fiats)
            val type: Type = object : TypeToken<Map<String, Map<String, BigDecimal>>>() {}.type
            val decodedData: Map<String, Map<String, BigDecimal>> =
                gson.fromJson(response, type)
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
    ): String {
        return withContext(Dispatchers.IO) {
            val client = OkHttpClient().newBuilder().retryOnConnectionFailure(true)
            val request = okhttp3.Request.Builder()
                .url(Endpoints.fetchCryptoPrices(coins, fiats))
                .addHeader("Content-Type", "application/json")
                .build()
            val response = client.build().newCall(request).execute()

            return@withContext response.body?.string() ?: ""
        }
    }

    private companion object {
        const val ERROR_CRYPTO_SERVICE = "ERROR CRYPTO SERVICE"
    }
}
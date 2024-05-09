package com.vultisig.wallet.data.repositories

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.vultisig.wallet.common.Endpoints
import com.vultisig.wallet.data.models.AppCurrency
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.lang.reflect.Type
import java.math.BigDecimal
import javax.inject.Inject

internal interface TokenPriceRepository {

    fun getPrice(
        priceProviderId: String,
        appCurrency: AppCurrency,
    ): Flow<BigDecimal>

    suspend fun refresh(
        priceProviderIds: List<String>,
    )

}

typealias CurrencyToPrice = Map<String, BigDecimal>

internal class TokenPriceRepositoryImpl @Inject constructor(
    private val gson: Gson,
    private val appCurrencyRepository: AppCurrencyRepository,
) : TokenPriceRepository {

    private val providerIdToPrice = MutableStateFlow(mapOf<String, CurrencyToPrice>())

    @ExperimentalCoroutinesApi
    override fun getPrice(
        priceProviderId: String,
        appCurrency: AppCurrency,
    ): Flow<BigDecimal> = providerIdToPrice.map {
        it[priceProviderId]
            ?.get(appCurrency.ticker.lowercase())
            ?: BigDecimal.ZERO
    }

    override suspend fun refresh(priceProviderIds: List<String>) {
        val priceProviderIdsParam = priceProviderIds.joinToString(",")
        val fiatParam = appCurrencyRepository.currency.first().ticker

        val response = fetchPrices(priceProviderIdsParam, fiatParam)

        val type: Type = object : TypeToken<Map<String, Map<String, BigDecimal>>>() {}.type
        val decodedData: Map<String, Map<String, BigDecimal>> =
            gson.fromJson(response, type)

        providerIdToPrice.value = decodedData
    }

    private suspend fun fetchPrices(
        coins: String,
        fiats: String,
    ): String = withContext(Dispatchers.IO) {
        val client = OkHttpClient().newBuilder().retryOnConnectionFailure(true)
        val request = Request.Builder()
            .url(Endpoints.fetchCryptoPrices(coins, fiats))
            .addHeader("Content-Type", "application/json")
            .build()
        val response = client.build().newCall(request).execute()

        return@withContext response.body?.string() ?: ""
    }

}
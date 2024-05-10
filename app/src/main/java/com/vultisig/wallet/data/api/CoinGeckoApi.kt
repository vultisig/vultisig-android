package com.vultisig.wallet.data.api

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.vultisig.wallet.common.Endpoints
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import java.math.BigDecimal
import javax.inject.Inject

internal typealias CurrencyToPrice = Map<String, BigDecimal>

internal interface CoinGeckoApi {

    suspend fun getCryptoPrices(
        priceProviderIds: List<String>,
        currencies: List<String>
    ): Map<String, CurrencyToPrice>

}

internal class CoinGeckoApiImpl @Inject constructor(
    private val http: HttpClient,
    private val gson: Gson,
) : CoinGeckoApi {

    override suspend fun getCryptoPrices(
        priceProviderIds: List<String>,
        currencies: List<String>
    ): Map<String, CurrencyToPrice> {
        val priceProviderIdsParam = priceProviderIds.joinToString(",")
        val currenciesParam = currencies.joinToString(",")

        val type = object : TypeToken<Map<String, CurrencyToPrice>>() {}.type
        return gson.fromJson(fetchPrices(priceProviderIdsParam, currenciesParam), type)
    }


    private suspend fun fetchPrices(
        coins: String,
        fiats: String,
    ): String = http
        .get("https://api.voltix.org/coingeicko/api/v3/simple/price") {
            parameter("ids", coins)
            parameter("vs_currencies", fiats)
            header("Content-Type", "application/json")
        }.bodyAsText()


}
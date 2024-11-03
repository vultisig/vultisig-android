package com.vultisig.wallet.data.api

import com.vultisig.wallet.data.models.Chain
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import java.math.BigDecimal
import java.net.UnknownHostException
import javax.inject.Inject

typealias CurrencyToPrice = Map<String, BigDecimal>

interface CoinGeckoApi {

    suspend fun getCryptoPrices(
        priceProviderIds: List<String>,
        currencies: List<String>,
    ): Map<String, CurrencyToPrice>

    suspend fun getContractsPrice(
        chain: Chain,
        contractAddresses: List<String>,
        currencies: List<String>,
    ): Map<String, CurrencyToPrice>

}

internal class CoinGeckoApiImpl @Inject constructor(
    private val http: HttpClient,
) : CoinGeckoApi {
    

    override suspend fun getCryptoPrices(
        priceProviderIds: List<String>,
        currencies: List<String>,
    ): Map<String, CurrencyToPrice> {
        val priceProviderIdsParam = priceProviderIds.joinToString(",")
        val currenciesParam = currencies.joinToString(",")
        return fetchPrices(
            priceProviderIdsParam,
            currenciesParam
        )
    }

    override suspend fun getContractsPrice(
        chain: Chain,
        contractAddresses: List<String>,
        currencies: List<String>,
    ): Map<String, CurrencyToPrice> {
        val priceProviderIdsParam = contractAddresses.joinToString(",")
        val currenciesParam = currencies.joinToString(",")
        return fetchContractPrices(
            chain.coinGeckoAssetId,
            priceProviderIdsParam,
            currenciesParam,
        )
    }

    private suspend fun fetchPrices(
        coins: String,
        fiats: String,
    ): Map<String, CurrencyToPrice> = try {
        http
            .get("https://api.vultisig.com/coingeicko/api/v3/simple/price") {
                parameter("ids", coins)
                parameter("vs_currencies", fiats)
                header("Content-Type", "application/json")
            }.body()
    } catch (e: UnknownHostException) {
        emptyMap()
    }

    private suspend fun fetchContractPrices(
        chainId: String,
        coins: String,
        fiats: String,
    ): Map<String, CurrencyToPrice> = try {
        http
            .get("https://api.vultisig.com/coingeicko/api/v3/simple/token_price/${chainId}") {
                parameter("contract_addresses", coins)
                parameter("vs_currencies", fiats)
                header("Content-Type", "application/json")
            }.body()
    } catch (e: UnknownHostException) {
        emptyMap()
    }

    private val Chain.coinGeckoAssetId: String
        get() = when (this) {
            Chain.Ethereum -> "ethereum"
            Chain.Avalanche -> "avalanche"
            Chain.Base -> "base"
            Chain.Blast -> "blast"
            Chain.Arbitrum -> "arbitrum-one"
            Chain.Polygon -> "polygon-pos"
            Chain.Optimism -> "optimistic-ethereum"
            Chain.BscChain -> "binance-smart-chain"
            Chain.ZkSync -> "zksync"

            else -> error("No CoinGecko asset id for chain $this")
        }

}
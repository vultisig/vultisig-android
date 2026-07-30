package com.vultisig.wallet.data.api

import com.vultisig.wallet.data.api.models.CoinMarketStatsJson
import com.vultisig.wallet.data.api.models.MarketChartResponseJson
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.coinGeckoAssetPlatformId
import com.vultisig.wallet.data.utils.bodyOrThrow
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.http.appendPathSegments
import java.math.BigDecimal
import javax.inject.Inject
import timber.log.Timber

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

    /**
     * `/coins/{id}/market_chart` — id lookups are case-sensitive on CoinGecko; [id] is lowercased
     * internally before the request, so callers don't need to normalize it themselves.
     */
    suspend fun getMarketChart(id: String, currency: String, days: String): MarketChartResponseJson

    /** `/coins/{platform}/contract/{address}/market_chart`, for tokens with no CoinGecko id. */
    suspend fun getContractMarketChart(
        chain: Chain,
        contractAddress: String,
        currency: String,
        days: String,
    ): MarketChartResponseJson

    /** `/coins/markets` — market cap, rank, FDV, volume, supply and price-extreme stats. */
    suspend fun getMarketStats(id: String, currency: String): List<CoinMarketStatsJson>
}

internal class CoinGeckoApiImpl @Inject constructor(private val http: HttpClient) : CoinGeckoApi {

    override suspend fun getCryptoPrices(
        priceProviderIds: List<String>,
        currencies: List<String>,
    ): Map<String, CurrencyToPrice> {
        val priceProviderIdsParam = priceProviderIds.joinToString(",")
        val currenciesParam = currencies.joinToString(",")
        return try {
            fetchPrices(priceProviderIdsParam, currenciesParam)
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Timber.d(e, "error occurred in getCryptoPrices")
            emptyMap()
        }
    }

    override suspend fun getContractsPrice(
        chain: Chain,
        contractAddresses: List<String>,
        currencies: List<String>,
    ): Map<String, CurrencyToPrice> {
        val priceProviderIdsParam = contractAddresses.joinToString(",")
        val currenciesParam = currencies.joinToString(",")
        return try {
            fetchContractPrices(chain.coinGeckoAssetId, priceProviderIdsParam, currenciesParam)
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Timber.d(e, "error occurred in getContractsPrice")
            emptyMap()
        }
    }

    private suspend fun fetchPrices(coins: String, fiats: String): Map<String, CurrencyToPrice> =
        http
            .get("https://api.vultisig.com/coingeicko/api/v3/simple/price") {
                parameter("ids", coins)
                parameter("vs_currencies", fiats)
                header("Content-Type", "application/json")
            }
            .body()

    private suspend fun fetchContractPrices(
        chainId: String,
        coins: String,
        fiats: String,
    ): Map<String, CurrencyToPrice> =
        http
            .get("https://api.vultisig.com/coingeicko/api/v3/simple/token_price/${chainId}") {
                parameter("contract_addresses", coins)
                parameter("vs_currencies", fiats)
                header("Content-Type", "application/json")
            }
            .body()

    override suspend fun getMarketChart(
        id: String,
        currency: String,
        days: String,
    ): MarketChartResponseJson =
        http
            .get("https://api.vultisig.com/coingeicko/api/v3/coins") {
                // id can come from remote, untrusted data (e.g. a Solana token list's
                // extensions.coingeckoId); encodeSlash = true stops a '/' in it from retargeting
                // the request path, same as getContractMarketChart below.
                url {
                    appendPathSegments(listOf(id.lowercase(), "market_chart"), encodeSlash = true)
                }
                parameter("vs_currency", currency)
                parameter("days", days)
                header("Content-Type", "application/json")
            }
            .bodyOrThrow()

    override suspend fun getContractMarketChart(
        chain: Chain,
        contractAddress: String,
        currency: String,
        days: String,
    ): MarketChartResponseJson =
        http
            .get("https://api.vultisig.com/coingeicko/api/v3/coins") {
                url {
                    // encodeSlash = true so a contractAddress containing '/' (defaults to false in
                    // Ktor, which would let it split into extra path segments) can't retarget the
                    // request path.
                    appendPathSegments(
                        listOf(chain.coinGeckoAssetId, "contract", contractAddress, "market_chart"),
                        encodeSlash = true,
                    )
                }
                parameter("vs_currency", currency)
                parameter("days", days)
                header("Content-Type", "application/json")
            }
            .bodyOrThrow()

    override suspend fun getMarketStats(id: String, currency: String): List<CoinMarketStatsJson> =
        http
            .get("https://api.vultisig.com/coingeicko/api/v3/coins/markets") {
                parameter("vs_currency", currency)
                parameter("ids", id.lowercase())
                parameter("price_change_percentage", "24h")
                header("Content-Type", "application/json")
            }
            .bodyOrThrow()

    private val Chain.coinGeckoAssetId: String
        get() = coinGeckoAssetPlatformId() ?: error("No CoinGecko asset id for chain $this")
}

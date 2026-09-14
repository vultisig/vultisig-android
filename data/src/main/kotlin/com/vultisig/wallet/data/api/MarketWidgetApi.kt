package com.vultisig.wallet.data.api

import com.vultisig.wallet.data.models.MarketWidgetAsset
import com.vultisig.wallet.data.models.MarketWidgetAssetIdentity
import com.vultisig.wallet.data.models.MarketWidgetQuery
import com.vultisig.wallet.data.utils.bodyOrThrow
import io.ktor.client.HttpClient
import io.ktor.client.plugins.timeout
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.Url
import io.ktor.http.contentLength
import io.ktor.http.isSuccess
import io.ktor.utils.io.readRemaining
import javax.inject.Inject
import kotlinx.io.readByteArray
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Market data for the home-screen widgets, served through the Vultisig CoinGecko proxy. Kept apart
 * from [CoinGeckoApi] because the widgets need the `/coins/markets` list shape with sparklines and
 * the `/search` endpoint, neither of which the in-app price paths use.
 */
interface MarketWidgetApi {

    suspend fun markets(query: MarketWidgetQuery, currency: String): List<MarketWidgetAsset>

    suspend fun search(query: String): List<MarketWidgetAssetIdentity>

    /**
     * Raw bytes of a token icon. Only `https://coin-images.coingecko.com` is fetched — any other
     * host, or an image over [MAX_ICON_BYTES], yields null so a hostile `image` field in the market
     * response can't turn the widget refresh into an arbitrary download.
     */
    suspend fun icon(url: String): ByteArray?

    companion object {
        const val MAX_ICON_BYTES = 64 * 1024
    }
}

class MarketWidgetEmptyResponseException : Exception("Market response contained no usable assets")

internal class MarketWidgetApiImpl @Inject constructor(private val http: HttpClient) :
    MarketWidgetApi {

    override suspend fun markets(
        query: MarketWidgetQuery,
        currency: String,
    ): List<MarketWidgetAsset> {
        val ids = query.normalizedIds
        require(query !is MarketWidgetQuery.Ids || ids.isNotEmpty()) { "No asset ids selected" }

        val records: List<RemoteMarketAssetJson> =
            http
                .get("$PROXY_BASE_URL/coins/markets") {
                    parameter("vs_currency", currency.lowercase())
                    parameter("order", "market_cap_desc")
                    parameter("per_page", query.limit)
                    parameter("page", 1)
                    parameter("sparkline", true)
                    parameter("price_change_percentage", "24h")
                    if (ids.isNotEmpty()) parameter("ids", ids.joinToString(","))
                    timeout { requestTimeoutMillis = MARKETS_TIMEOUT_MS }
                }
                .bodyOrThrow()

        val assets = records.mapNotNull { it.toAsset() }
        if (assets.isEmpty()) throw MarketWidgetEmptyResponseException()

        return when (query) {
            is MarketWidgetQuery.Top -> assets.take(query.limit)
            is MarketWidgetQuery.Ids -> {
                // CoinGecko returns ids in market-cap order; the widget shows them in the order
                // the user picked them.
                val position = ids.withIndex().associate { (index, id) -> id to index }
                assets.sortedBy { position[it.id] ?: Int.MAX_VALUE }
            }
        }
    }

    override suspend fun search(query: String): List<MarketWidgetAssetIdentity> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return emptyList()
        val response: RemoteSearchResponseJson =
            http
                .get("$PROXY_BASE_URL/search") {
                    parameter("query", trimmed)
                    timeout { requestTimeoutMillis = SEARCH_TIMEOUT_MS }
                }
                .bodyOrThrow()
        return response.coins.take(MAX_SEARCH_RESULTS).map {
            MarketWidgetAssetIdentity(
                id = it.id.lowercase(),
                symbol = it.symbol.uppercase(),
                name = it.name,
            )
        }
    }

    override suspend fun icon(url: String): ByteArray? {
        val validated = validatedIconUrl(url) ?: return null
        // Streamed rather than `bodyAsBytes()`: Content-Length is only a hint (it can be absent
        // on a chunked response), and the cap must hold before the body is ever buffered whole.
        return http
            .prepareGet(validated) { timeout { requestTimeoutMillis = SEARCH_TIMEOUT_MS } }
            .execute { response ->
                if (!response.status.isSuccess()) return@execute null
                val declaredLength = response.contentLength()
                if (declaredLength != null && declaredLength > MarketWidgetApi.MAX_ICON_BYTES) {
                    return@execute null
                }
                val bytes =
                    response
                        .bodyAsChannel()
                        .readRemaining(MarketWidgetApi.MAX_ICON_BYTES + 1L)
                        .readByteArray()
                bytes.takeIf { it.isNotEmpty() && it.size <= MarketWidgetApi.MAX_ICON_BYTES }
            }
    }

    private fun RemoteMarketAssetJson.toAsset(): MarketWidgetAsset? {
        val price = currentPrice?.takeIf { it.isFinite() } ?: return null
        val series = sparklineIn7d?.price.orEmpty().filter { it.isFinite() }
        return MarketWidgetAsset(
            id = id.lowercase(),
            symbol = symbol.uppercase(),
            name = name,
            imageUrl = image?.let { validatedIconUrl(it) },
            currentPrice = price,
            priceChangePercentage24h = priceChangePercentage24h,
            marketCapRank = marketCapRank,
            sparkline = resampleSparkline(series, MarketWidgetQuery.SPARKLINE_POINTS),
        )
    }

    companion object {
        private const val PROXY_BASE_URL = "https://api.vultisig.com/coingeicko/api/v3"
        private const val APPROVED_ICON_HOST = "coin-images.coingecko.com"
        private const val MARKETS_TIMEOUT_MS = 12_000L
        private const val SEARCH_TIMEOUT_MS = 8_000L
        private const val MAX_SEARCH_RESULTS = 20

        /** Returns [raw] when it is a plain https URL on the approved icon host, else null. */
        internal fun validatedIconUrl(raw: String): String? {
            val url = runCatching { Url(raw) }.getOrNull() ?: return null
            val isApproved =
                url.protocol.name == "https" &&
                    url.host.equals(APPROVED_ICON_HOST, ignoreCase = true) &&
                    url.user == null &&
                    url.password == null &&
                    url.specifiedPort == 0
            return raw.takeIf { isApproved }
        }

        /**
         * Linearly resamples a 7-day series (CoinGecko sends ~168 hourly points) down to
         * [targetCount] so the cache stays small and the sparkline draws a fixed point count.
         */
        internal fun resampleSparkline(values: List<Double>, targetCount: Int): List<Double> {
            if (targetCount <= 1 || values.size <= targetCount) return values
            val interval = (values.size - 1).toDouble() / (targetCount - 1)
            return List(targetCount) { outputIndex ->
                val sourcePosition = outputIndex * interval
                val lower = sourcePosition.toInt()
                val upper = minOf(lower + 1, values.size - 1)
                val fraction = sourcePosition - lower
                values[lower] + (values[upper] - values[lower]) * fraction
            }
        }
    }
}

@Serializable
private data class RemoteMarketAssetJson(
    val id: String,
    val symbol: String,
    val name: String,
    val image: String? = null,
    @SerialName("current_price") val currentPrice: Double? = null,
    @SerialName("price_change_percentage_24h") val priceChangePercentage24h: Double? = null,
    @SerialName("market_cap_rank") val marketCapRank: Int? = null,
    @SerialName("sparkline_in_7d") val sparklineIn7d: RemoteSparklineJson? = null,
)

@Serializable private data class RemoteSparklineJson(val price: List<Double> = emptyList())

@Serializable
private data class RemoteSearchResponseJson(val coins: List<RemoteSearchCoinJson> = emptyList())

@Serializable
private data class RemoteSearchCoinJson(val id: String, val name: String, val symbol: String)

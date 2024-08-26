package com.vultisig.wallet.data.api

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.vultisig.wallet.models.Chain
import com.vultisig.wallet.models.Coin
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import timber.log.Timber
import java.math.BigDecimal
import javax.inject.Inject

internal interface CmcApi {

    suspend fun fetchPrice(
        coin: Coin,
        currency: String
    ): CurrencyToPrice

    suspend fun fetchPrices(
        coins: List<Coin>,
        currency: String
    ): Map<String, CurrencyToPrice>

}

internal class CmcApiImpl @Inject constructor(
    private val http: HttpClient,
    private val gson: Gson,
) : CmcApi {

    override suspend fun fetchPrice(coin: Coin, currency: String): CurrencyToPrice {
        val cmcId = fetchTokenCmcId(coin)
        val cmcPriceResponse = cmcPriceResponse(cmcId.toString(), currency)
        val price = extractPriceFromCmcPriceResponse(currency, cmcPriceResponse, cmcId)
        return mapOf(currency to price)
    }

    override suspend fun fetchPrices(
        coins: List<Coin>,
        currency: String
    ) = coroutineScope {
        val cmcIds = coins.map { coin ->
            async {
                coin to fetchTokenCmcId(coin)
            }
        }.awaitAll()
        fetchTokenPrice(currency, cmcIds)
    }


    private suspend fun fetchTokenPrice(
        currency: String,
        coinAndIds: List<Pair<Coin, Int?>>,
    ): Map<String, CurrencyToPrice> {
        val cmcIds = coinAndIds
            .mapNotNull { (_, cmcId) -> cmcId }
            .joinToString(",")
        val cmcPriceResponse = cmcPriceResponse(cmcIds, currency)
        return coinAndIds.associate { (eachCoin, cmcId) ->
            eachCoin.id to mapOf(
                currency to extractPriceFromCmcPriceResponse(currency, cmcPriceResponse, cmcId)
            )
        }
    }

    private fun extractPriceFromCmcPriceResponse(
        currency: String,
        cmcPriceResponse: JsonObject,
        cmcId: Int?
    ) = try {
        cmcPriceResponse
            .getAsJsonObject(cmcId.toString())
            .getAsJsonObject("quote")
            .getAsJsonObject(currency.uppercase())
            .getAsJsonPrimitive("price").asBigDecimal
    } catch (e: Exception) {
        Timber.tag(this::class.simpleName!!).e(e, "can not find price in cmc response")
        BigDecimal.ZERO
    }

    private suspend fun cmcPriceResponse(
        cmcIds: String,
        currency: String
    ): JsonObject {
        val response = http.get("https://api.vultisig.com/cmc/v2/cryptocurrency/quotes/latest") {
            parameter("id", cmcIds)
            parameter("skip_invalid", true)
            parameter("aux", "is_active")
            parameter("convert", currency)
        }
        return gson.fromJson(response.bodyAsText(), JsonObject::class.java)
            .getAsJsonObject("data")
    }

    private suspend fun fetchTokenCmcId(coin: Coin): Int? {
        return if (!coin.isNativeToken) {
            try {
                val response = http.get("https://api.vultisig.com/cmc/v1/cryptocurrency/info") {
                    parameter("address", coin.contractAddress)
                }
                val bodyAsText = response.bodyAsText()

                gson.fromJson(
                    bodyAsText,
                    JsonObject::class.java
                )
                    .getAsJsonObject("data").keySet()
                    .first().toInt()
            } catch (e: Exception) {
                Timber.tag(this::class.simpleName!!).e(e, "can not find cmc id for token")
                return null
            }
        } else {
            coin.getNativeTokenCmcId()
        }
    }


    private fun Coin.getNativeTokenCmcId(): Int = when (chain) {
        Chain.Arbitrum -> 1027
        Chain.Avalanche -> 5805
        Chain.Base -> 1027
        Chain.Bitcoin -> 1
        Chain.Blast -> 1027
        Chain.BitcoinCash -> 1831
        Chain.BscChain -> 1839
        Chain.CronosChain -> 3635
        Chain.Dogecoin -> 74
        Chain.Dydx -> 28324
        Chain.Dash -> 131
        Chain.Ethereum -> 1027
        Chain.GaiaChain -> 3794
        Chain.Kujira -> 15185
        Chain.Litecoin -> 2
        Chain.MayaChain -> 23534
        Chain.Optimism -> 1027
        Chain.Polkadot -> 6636
        Chain.Polygon -> 3890
        Chain.Solana -> 5426
        Chain.ThorChain -> 4157
    }

}
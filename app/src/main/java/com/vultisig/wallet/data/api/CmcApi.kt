package com.vultisig.wallet.data.api

import com.vultisig.wallet.data.repositories.LastOpenedVaultRepository
import com.vultisig.wallet.data.repositories.VaultRepository
import com.vultisig.wallet.models.Chain
import com.vultisig.wallet.models.Coin
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import timber.log.Timber
import java.math.BigDecimal
import javax.inject.Inject

internal interface CmcApi {

    suspend fun fetchPrice(
        coin: Coin, currency: String
    ): BigDecimal

    suspend fun fetchPrices(
        coins: List<Coin>, currency: String
    ): Map<String, BigDecimal>

}


internal class CmcApiImpl @Inject constructor(
    private val http: HttpClient,
    private val vaultRepository: VaultRepository,
    private val lastOpenedVaultRepository: LastOpenedVaultRepository,
) : CmcApi {

    override suspend fun fetchPrice(coin: Coin, currency: String): BigDecimal {
        val cmcId = fetchTokenCmcId(coin)
        val cmcPriceResponse = cmcPriceResponse(cmcId.toString(), currency)
        return extractCoinAndPrice(cmcPriceResponse, coin)
            .values.firstOrNull() ?: BigDecimal.ZERO
    }

    override suspend fun fetchPrices(
        coins: List<Coin>, currency: String
    ) = coroutineScope {
        coins.filter { it.cmcId == null }.map { coin ->
            async {
                val cmcId = fetchTokenCmcId(coin)
                coin.copy(cmcId = cmcId)
            }
        }.awaitAll().apply {
            val updatedCoins = filter { it.cmcId != null }
            if (updatedCoins.isEmpty()) return@apply
            saveToDatabase(updatedCoins)
        }
        fetchTokenPrice(currency, coins)
    }


    private suspend fun saveToDatabase(updatedCoins: List<Coin>) {
        val vaultId = lastOpenedVaultRepository.lastOpenedVaultId.first()
        vaultId?.let {
            vaultRepository.upsertCoins(vaultId, updatedCoins)
        }
    }


    private suspend fun fetchTokenPrice(
        currency: String,
        coins: List<Coin>,
    ): Map<String, BigDecimal> {
        val cmcIds = coins.mapNotNull { it.cmcId }.joinToString(",")
        val cmcPriceResponse = cmcPriceResponse(cmcIds, currency)
        return extractCoinAndPrice(cmcPriceResponse, coins)
    }

    private fun extractCoinAndPrice(
        cmcPriceResponse: JsonElement?,
        coins: List<Coin>
    ): Map<String, BigDecimal> {
        // Due to dynamic response keys, a data class is not feasible. example response:
        // "1027": { "id": 1027, ... ,"quote": { "USD": {  "price": 2685.777349396061,... } } }
        return convertRespToTokenToPriceList(cmcPriceResponse)
            ?.associate { (cmcId, price) ->
                val coin = coins.find { it.cmcId == cmcId.toInt() }!!
                coin.id to (price ?: BigDecimal.ZERO)
            } ?: emptyMap()
    }

    private fun extractCoinAndPrice(
        cmcPriceResponse: JsonElement?,
        coin: Coin
    ): Map<String, BigDecimal> {
        return convertRespToTokenToPriceList(cmcPriceResponse)
            ?.associate { (_, price) -> coin.id to (price ?: BigDecimal.ZERO) } ?: emptyMap()
    }

    private fun convertRespToTokenToPriceList(cmcPriceResponse: JsonElement?) =
        cmcPriceResponse?.jsonObject?.map {
            it.key to it.value.jsonObject["quote"]?.jsonObject?.toMap()?.values?.firstOrNull()
                ?.jsonObject?.get("price")?.jsonPrimitive?.contentOrNull?.toBigDecimal()
        }

    private suspend fun cmcPriceResponse(
        cmcIds: String, currency: String
    ): JsonElement? {
        try {
            val response = http.get("https://api.vultisig.com/cmc/v2/cryptocurrency/quotes/latest") {
                parameter("id", cmcIds)
                parameter("skip_invalid", true)
                parameter("aux", "is_active")
                parameter("convert", currency)
            }
            return response.body<JsonObject>()["data"]
        } catch (e: Exception) {
            Timber.tag("CmcApiImpl").e(e, "can not get token price")
            return null
        }
    }

    private suspend fun fetchTokenCmcId(coin: Coin): Int? {
        return if (!coin.isNativeToken) {
            try {
                val response = http.get("https://api.vultisig.com/cmc/v1/cryptocurrency/info") {
                    parameter("address", coin.contractAddress)
                }.body<JsonObject>()
                response["data"]?.jsonObject?.keys?.first()?.toInt()
            } catch (e: Exception) {
                Timber.tag("CmcApiImpl").e(e, "can not find cmc id for token ${coin.id}")
                null
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
        Chain.ZkSync -> 1027
    }

}
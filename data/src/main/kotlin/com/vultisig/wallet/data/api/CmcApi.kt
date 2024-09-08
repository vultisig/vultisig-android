package com.vultisig.wallet.data.api

import com.vultisig.wallet.data.api.models.CmcIdResponseJson
import com.vultisig.wallet.data.api.models.CmcPriceResponseJson
import com.vultisig.wallet.data.db.dao.CmcIdDao
import com.vultisig.wallet.data.db.models.CmcIdEntity
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.CoinCmcPrice
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import timber.log.Timber
import java.math.BigDecimal
import javax.inject.Inject

interface CmcApi {

    suspend fun fetchPrice(
        coin: CoinCmcPrice,
        currency: String
    ): BigDecimal

    suspend fun fetchPrices(
        coins: List<CoinCmcPrice>,
        currency: String
    ): Map<String, BigDecimal>

}


class CmcApiImpl @Inject constructor(
    private val http: HttpClient,
    private val cmcIdDao: CmcIdDao,
) : CmcApi {

    override suspend fun fetchPrice(
        coin: CoinCmcPrice,
        currency: String
    ): BigDecimal {
        return fetchTokenCmcId(coin)?.let { cmcId ->
            val cmcPriceResponse = fetchCmcPriceResponse(listOf(cmcId), currency)
            extractCoinAndPrice(cmcPriceResponse, coin).values.firstOrNull()
                ?: BigDecimal.ZERO
        } ?: BigDecimal.ZERO
    }


    override suspend fun fetchPrices(
        coins: List<CoinCmcPrice>,
        currency: String
    ) = coroutineScope {
        val coinCmcPrices = coins.map { coin ->
            async {
                if (coin.cmcId == null) {
                    val cmcId = fetchTokenCmcId(coin)
                    coin.copy(cmcId = cmcId).apply {
                        if (!isNativeToken && cmcId != null)
                            saveToDatabase(this)
                    }
                } else coin
            }
        }.awaitAll()
        extractTokenPrice(currency, coinCmcPrices)
    }


    private suspend fun saveToDatabase(coin: CoinCmcPrice) {
        cmcIdDao.insertCmcId(
            CmcIdEntity(
                contractAddress = coin.contractAddress,
                id = coin.cmcId,
            )
        )
    }


    private suspend fun extractTokenPrice(
        currency: String,
        coins: List<CoinCmcPrice>,
    ): Map<String, BigDecimal> {
        val cmcIds = coins.mapNotNull { it.cmcId }
        val cmcPriceResponse = fetchCmcPriceResponse(cmcIds, currency)
        return extractCoinAndPrice(cmcPriceResponse, coins)
    }

    private fun extractCoinAndPrice(
        cmcPriceResponse: CmcPriceResponseJson?,
        coins: List<CoinCmcPrice>
    ): Map<String, BigDecimal> {
        return convertRespToTokenToPriceList(cmcPriceResponse)?.associate { (cmcId, price) ->
            val coin = coins.first { it.cmcId == cmcId.toInt() }
            coin.tokenId to (price ?: BigDecimal.ZERO)
        } ?: emptyMap()
    }

    private fun extractCoinAndPrice(
        cmcPriceResponse: CmcPriceResponseJson?,
        coin: CoinCmcPrice
    ) = convertRespToTokenToPriceList(cmcPriceResponse)?.associate { (_, price) ->
        coin.tokenId to (price ?: BigDecimal.ZERO)
    } ?: emptyMap()


    private fun convertRespToTokenToPriceList(cmcPriceResponse: CmcPriceResponseJson?) =
        cmcPriceResponse?.data?.map { (cmcId, data) ->
            cmcId to data.quote.values.firstOrNull()?.price
        }

    private suspend fun fetchCmcPriceResponse(
        cmcIds: List<Int>,
        currency: String
    ): CmcPriceResponseJson? {
        try {
            val response =
                http.get("https://api.vultisig.com/cmc/v2/cryptocurrency/quotes/latest") {
                    parameter("id", cmcIds.joinToString(","))
                    parameter("skip_invalid", true)
                    parameter("aux", "is_active")
                    parameter("convert", currency)
                }
            return response.body<CmcPriceResponseJson>()
        } catch (e: Exception) {
            Timber.e(e, "can not get token price")
            return null
        }
    }


    private suspend fun fetchTokenCmcId(coin: CoinCmcPrice) =
        if (coin.contractAddress.isNotBlank()) {
            try {
                val response = http.get("https://api.vultisig.com/cmc/v1/cryptocurrency/info") {
                    parameter("address", coin.contractAddress)
                }.body<CmcIdResponseJson>()
                response.data.values.first().id
            } catch (e: Exception) {
                Timber.e(e, "can not find cmc id for token ${coin.tokenId}")
                null
            }
        } else {
            coin.chain.getNativeTokenCmcId()
        }


    private fun Chain.getNativeTokenCmcId() =
        when (this) {
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
            else -> error("cmc id not defined for native token $id")
        }
}



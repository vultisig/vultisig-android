package com.vultisig.wallet.data.repositories

import com.vultisig.wallet.data.api.CoinGeckoApi
import com.vultisig.wallet.data.api.CurrencyToPrice
import com.vultisig.wallet.data.db.dao.TokenPriceDao
import com.vultisig.wallet.data.db.models.TokenPriceEntity
import com.vultisig.wallet.data.models.AppCurrency
import com.vultisig.wallet.models.Chain
import com.vultisig.wallet.models.Coin
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import java.math.BigDecimal
import javax.inject.Inject

internal interface TokenPriceRepository {

    suspend fun getCachedPrice(
        tokenId: String,
        appCurrency: AppCurrency,
    ): BigDecimal?

    suspend fun getCachedPrices(
        tokenIds: List<String>,
        appCurrency: AppCurrency,
    ): List<Pair<String, BigDecimal>>

    fun getPrice(
        token: Coin,
        appCurrency: AppCurrency,
    ): Flow<BigDecimal>

    suspend fun refresh(
        tokens: List<Coin>,
    )

}


internal class TokenPriceRepositoryImpl @Inject constructor(
    private val appCurrencyRepository: AppCurrencyRepository,
    private val coinGeckoApi: CoinGeckoApi,
    private val tokenPriceDao: TokenPriceDao,
) : TokenPriceRepository {

    private val tokenIdToPrice = MutableStateFlow(mapOf<String, CurrencyToPrice>())

    override suspend fun getCachedPrice(
        tokenId: String,
        appCurrency: AppCurrency
    ): BigDecimal? = tokenPriceDao
        .getTokenPrice(tokenId, appCurrency.ticker.lowercase())
        ?.let { BigDecimal(it) }

    override suspend fun getCachedPrices(
        tokenIds: List<String>,
        appCurrency: AppCurrency
    ): List<Pair<String, BigDecimal>> = tokenPriceDao
        .getTokenPrices(tokenIds, appCurrency.ticker.lowercase())
        .map { it.tokenId to BigDecimal(it.price) }

    @ExperimentalCoroutinesApi
    override fun getPrice(
        token: Coin,
        appCurrency: AppCurrency,
    ): Flow<BigDecimal> = tokenIdToPrice.map {
        it[token.id]
            ?.get(appCurrency.ticker.lowercase())
            ?: BigDecimal.ZERO
    }

    override suspend fun refresh(tokens: List<Coin>) {
        val currency = appCurrencyRepository.currency.first().ticker.lowercase()
        val currencies = listOf(currency)

        val tokensByPriceProviderIds = tokens.associateBy { it.priceProviderID }
        val tokensByContractAddress = tokens.associateBy { it.contractAddress }

        val priceProviderIds = mutableListOf<String>()
        val chainContractAddresses = mutableMapOf<Chain, List<Coin>>()

        // sort tokens with contract address and price provider id to different lists
        tokens.forEach { token ->
            if (token.priceProviderID.isNotEmpty()) {
                priceProviderIds.add(token.priceProviderID)
            } else {
                val existingChain = chainContractAddresses
                    .getOrPut(token.chain) { mutableListOf() }
                chainContractAddresses[token.chain] = existingChain + token
            }
        }

        val pricesWithProviderIds = coinGeckoApi.getCryptoPrices(priceProviderIds, currencies)
            .asSequence()
            .mapNotNull { (priceProviderId, value) ->
                val tokenId = tokensByPriceProviderIds[priceProviderId]?.id
                if (tokenId != null) {
                    tokenId to value
                } else null
            }
            .toMap()

        savePrices(pricesWithProviderIds, currency)

        chainContractAddresses
            .map { (chain, tokens) ->
                val pricesWithContractAddress = fetchPricesWithContractAddress(
                    chain = chain,
                    tokens = tokens,
                    currencies = currencies,
                ).asSequence()
                    .mapNotNull { (contractAddress, value) ->
                        val tokenId = tokensByContractAddress[contractAddress]?.id
                        if (tokenId != null) {
                            tokenId to value
                        } else null
                    }
                    .toMap()

                savePrices(pricesWithContractAddress, currency)
            }
    }

    private suspend fun savePrices(
        tokenIdToPrices: Map<String, CurrencyToPrice>,
        currency: String,
    ) {
        tokenIdToPrices.forEach { (tokenId, currencyToPrice) ->
            currencyToPrice[currency]?.toPlainString()?.let { price ->
                tokenPriceDao.insertTokenPrice(
                    TokenPriceEntity(
                        tokenId = tokenId,
                        currency = currency,
                        price = price,
                    )
                )
            }
        }

        tokenIdToPrice.update { it + tokenIdToPrices }
    }

    private suspend fun fetchPricesWithContractAddress(
        chain: Chain,
        tokens: List<Coin>,
        currencies: List<String>,
    ): Map<String, CurrencyToPrice> =
        coinGeckoApi.getContractsPrice(
            chain = chain,
            contractAddresses = tokens.map { it.contractAddress },
            currencies = currencies,
        )

}
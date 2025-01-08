package com.vultisig.wallet.data.repositories

import com.vultisig.wallet.data.api.CoinGeckoApi
import com.vultisig.wallet.data.api.CurrencyToPrice
import com.vultisig.wallet.data.api.LiQuestApi
import com.vultisig.wallet.data.db.dao.TokenPriceDao
import com.vultisig.wallet.data.db.models.TokenPriceEntity
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.settings.AppCurrency
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import java.math.BigDecimal
import javax.inject.Inject

interface TokenPriceRepository {

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

    suspend fun getPriceByContactAddress(
        chainId: String,
        contractAddress: String,
    ): BigDecimal

    suspend fun getPriceByPriceProviderId(
        priceProviderId: String,
    ): BigDecimal
}


internal class TokenPriceRepositoryImpl @Inject constructor(
    private val appCurrencyRepository: AppCurrencyRepository,
    private val coinGeckoApi: CoinGeckoApi,
    private val liQuestApi: LiQuestApi,
    private val tokenPriceDao: TokenPriceDao,
) : TokenPriceRepository {

    private val tokenIdToPrice = MutableStateFlow(mapOf<String, CurrencyToPrice>())

    override suspend fun getCachedPrice(
        tokenId: String,
        appCurrency: AppCurrency,
    ): BigDecimal? = tokenPriceDao
        .getTokenPrice(tokenId, appCurrency.ticker.lowercase())
        ?.let { BigDecimal(it) }

    override suspend fun getCachedPrices(
        tokenIds: List<String>,
        appCurrency: AppCurrency,
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

        val tokensByPriceProviderIds = tokens.groupBy { it.priceProviderID }
        val tokensByContractAddress = tokens.associateBy { it.contractAddress.lowercase() }

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
                val tokenIds = tokensByPriceProviderIds[priceProviderId]?.map { it.id }
                tokenIds?.map { tokenId -> tokenId to value }
            }
            .flatten()
            .toMap()

        savePrices(pricesWithProviderIds, currency)

        chainContractAddresses
            .map { (chain, tokens) ->
                val pricesWithContractAddress = fetchPricesWithContractAddress(
                    chain = chain,
                    contractAddresses = tokens.map { it.contractAddress },
                    currencies = currencies,
                ).asSequence()
                    .mapNotNull { (contractAddress, value) ->
                        val tokenId = tokensByContractAddress[contractAddress.lowercase()]?.id
                        if (
                            tokenId != null &&
                            value.filter { it.value != BigDecimal.ZERO }.isNotEmpty()
                        ) {
                            tokenId to value
                        } else null
                    }
                    .toMap()

                savePrices(pricesWithContractAddress, currency)
            }
    }

    override suspend fun getPriceByContactAddress(
        chainId: String,
        contractAddress: String,
    ): BigDecimal {
        val currency = appCurrencyRepository.currency.first().ticker.lowercase()
        val priceAndContract = fetchPriceWithContractAddress(
            Chain.fromRaw(chainId),
            contractAddress,
            currency
        )
        if (!priceAndContract.isNullOrEmpty()) {
            savePrices(
                mapOf(contractAddress to priceAndContract),
                currency
            )
            return priceAndContract.values.first()
        }
        return BigDecimal.ZERO
    }

    override suspend fun getPriceByPriceProviderId(priceProviderId: String): BigDecimal {
        val currency = appCurrencyRepository.currency.first().ticker.lowercase()
        val cryptoPrices = coinGeckoApi.getCryptoPrices(listOf(priceProviderId), listOf(currency))
        return cryptoPrices.values.firstOrNull()?.values?.firstOrNull() ?: BigDecimal.ZERO
    }

    private suspend fun savePrices(
        tokenIdToPrices: Map<String, CurrencyToPrice>,
        currency: String,
    ) {
        val tokenIdToPricesFiltered = tokenIdToPrices.filter {
                (_, currencyToPrice) -> currencyToPrice.isNotEmpty()
        }
        tokenIdToPricesFiltered.forEach { (tokenId, currencyToPrice) ->
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

        tokenIdToPrice.update { it + tokenIdToPricesFiltered }
    }

    private suspend fun fetchPricesWithContractAddress(
        chain: Chain,
        contractAddresses: List<String>,
        currencies: List<String>,
    ): Map<String, CurrencyToPrice> {
        return coroutineScope {
            val coinGeckoContractsPrice = coinGeckoApi.getContractsPrice(
                chain = chain,
                contractAddresses = contractAddresses,
                currencies = currencies,
            )
            val notInCoinGeckoTokens = contractAddresses.filterNot { address ->
                coinGeckoContractsPrice.keys.any { key ->
                    key.equals(
                        address,
                        false
                    )
                }
            }

            notInCoinGeckoTokens.takeIf { it.isNotEmpty() }
                ?: return@coroutineScope coinGeckoContractsPrice

            val tetherPrice = fetchTetherPrice()
            val currency = currencies.first()
            val lifiContractsPrice = notInCoinGeckoTokens
                .map { contractAddress ->
                    async {
                        contractAddress to getLifiContractPriceInUsd(
                            chain,
                            contractAddress
                        )
                    }
                }.awaitAll().associate { (contractAddress, priceInUsd) ->
                    //Since Lifi provides prices in USD, we use USDT to convert them into the local currency
                    contractAddress to mapOf(
                        currency to (priceInUsd?.times(tetherPrice) ?: BigDecimal.ZERO)
                    )
                }
            coinGeckoContractsPrice + lifiContractsPrice
        }
    }

    private suspend fun getLifiContractPriceInUsd(
        chain: Chain,
        contract: String,
    ): BigDecimal? = try {
        BigDecimal(
            liQuestApi.getLifiContractPriceUsd(
                chain, contract
            ).priceUsd
        )
    } catch (e: Exception) {
        null
    }

    private suspend fun fetchPriceWithContractAddress(
        chain: Chain,
        contractAddress: String,
        currency: String,
    ): CurrencyToPrice? =
        fetchPricesWithContractAddress(
            chain = chain,
            contractAddresses = listOf(contractAddress),
            currencies = listOf(currency),
        ).values.firstOrNull()


    private suspend fun fetchTetherPrice() =
        getPriceByPriceProviderId(TETHER_PRICE_PROVIDER_ID)



    companion object {
        private const val TETHER_PRICE_PROVIDER_ID = "tether"
    }

}
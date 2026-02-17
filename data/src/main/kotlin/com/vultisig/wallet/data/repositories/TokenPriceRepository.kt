package com.vultisig.wallet.data.repositories

import com.vultisig.wallet.data.api.CoinGeckoApi
import com.vultisig.wallet.data.api.CurrencyToPrice
import com.vultisig.wallet.data.api.LiQuestApi
import com.vultisig.wallet.data.api.ThorChainApi
import com.vultisig.wallet.data.db.dao.TokenPriceDao
import com.vultisig.wallet.data.db.models.TokenPriceEntity
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.Coins
import com.vultisig.wallet.data.models.TokenId
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
import kotlinx.coroutines.supervisorScope
import timber.log.Timber
import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode
import javax.inject.Inject
import kotlin.let
import kotlin.times
import kotlin.toBigDecimal

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

    suspend fun storeJupiterTokenPrice(
        tokenId: String,
        usdPrice: Double
    )

}


internal class TokenPriceRepositoryImpl @Inject constructor(
    private val appCurrencyRepository: AppCurrencyRepository,
    private val coinGeckoApi: CoinGeckoApi,
    private val liQuestApi: LiQuestApi,
    private val thorApi: ThorChainApi,
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

        val tokensByPriceProviderIds = tokens.groupBy { it.priceProviderID.lowercase() }
        val tokensByContractAddress = tokens.associateBy { it.contractAddress.lowercase() }

        val priceProviderIds = mutableListOf<String>()
        val chainContractAddresses = mutableMapOf<Chain, List<Coin>>()

        val usdPrices : MutableMap<TokenId, BigDecimal> = mutableMapOf()
        val tetherPrice = if (currency == AppCurrency.USD.ticker.lowercase()) {
            1.toBigDecimal()
        } else {
            fetchTetherPrice()
        }
        // sort tokens with contract address and price provider id to different lists
        tokens.forEach { token ->
            if (token.priceProviderID.isNotEmpty() && token.priceProviderID.toDoubleOrNull() == null) {
                priceProviderIds.add(token.priceProviderID)
            } else {
                token.priceProviderID.toBigDecimalOrNull()?.let { priceUsd ->
                    val tokenIdToPrices: Map<TokenId, CurrencyToPrice> = mapOf(
                        token.id to mapOf(currency to priceUsd * tetherPrice)
                    )
                    savePrices(
                        tokenIdToPrices,
                        currency = currency
                    )
                }
                val existingChain = chainContractAddresses
                    .getOrPut(token.chain) { mutableListOf() }
                chainContractAddresses[token.chain] = existingChain + token
            }
        }

        usdPrices.asSequence().mapNotNull {(tokenId, priceUsd) ->
            // Pair<TokenId, Map<String, BigDecimal>>
//  Map<TokenId, CurrencyToPrice>
            
        val  test= usdPrices.mapValues { (_, priceUsd) ->
            mapOf(currency to priceUsd * tetherPrice)
        }



        }


        val pricesWithProviderIds = coinGeckoApi.getCryptoPrices(priceProviderIds, currencies)
            .asSequence()
            .mapNotNull { (priceProviderId, value) ->
                val tokenIds = tokensByPriceProviderIds[priceProviderId.lowercase()]?.map { it.id }
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

                //here
                savePrices(pricesWithContractAddress, currency)
            }

        fetchThorPoolPrices(
            tokenList = tokens,
            currency = currency,
        )

        fetchThorContractPrices(
            currency = currency,
            tokenList = tokens,
        )
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

    override suspend fun storeJupiterTokenPrice(
        tokenId: String,
        usdPrice: Double
    ) {
        val currency = appCurrencyRepository.currency.first().ticker.lowercase()

        // Convert USD price to the user's currency
        val priceInUserCurrency = if (currency == "usd") {
            BigDecimal.valueOf(usdPrice)
        } else {
            val tetherPrice = fetchTetherPrice()
            BigDecimal.valueOf(usdPrice) * tetherPrice
        }

        tokenPriceDao.insertTokenPrice(
            TokenPriceEntity(
                tokenId = tokenId,
                currency = currency,
                price = priceInUserCurrency.toPlainString(),
            )
        )
    }

    private suspend fun savePrices(
        tokenIdToPrices: Map<TokenId, CurrencyToPrice>,
        currency: String,
    ) {
        val tokenIdToPricesFiltered = tokenIdToPrices.filter { (_, currencyToPrice) ->
            currencyToPrice.isNotEmpty()
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


     suspend fun fetchTetherPrice() =
        getPriceByPriceProviderId(TETHER_PRICE_PROVIDER_ID)

    private suspend fun fetchThorPoolPrices(tokenList: List<Coin>, currency: String) {
        supervisorScope {
            // if we have any thorchain tokens, then fetch their pool prices
            val thorTokens = tokenList.filter { it.chain == Chain.ThorChain && !it.isNativeToken }
            if (thorTokens.isEmpty()) return@supervisorScope // no tokens, no api request

            val poolAssetToPriceMap = try {
                thorApi.getPools()
                    .associate {
                        it.asset.lowercase() to it.assetTorPrice
                    }
            } catch (e: Exception) {
                Timber.e(e, "Failed to fetch prices from pools")
                return@supervisorScope
            }
            val tokenIdToPrices = buildTokenPriceMap(thorTokens,poolAssetToPriceMap,currency)

            savePrices(
                tokenIdToPrices,
                currency
            )
        }
    }



private suspend fun buildTokenPriceMap(
    thorTokens: List<Coin>,
    poolAssetToPriceMap:  Map<String, BigInteger>,
    currency: String
):  Map<TokenId, Map<String, BigDecimal>> {
    val tickerUsd = AppCurrency.USD.ticker.lowercase()
    val tetherPrice = if (currency.equals(tickerUsd, ignoreCase = true))
        1.toBigDecimal()
    else fetchTetherPrice()

    return thorTokens.asSequence()
        .mapNotNull {
            val mappedAsset = mapThorPoolAsset(it.contractAddress)
            var priceUsd = poolAssetToPriceMap[mappedAsset]?.toBigDecimal(scale = 8)

            // Fall back to ticker-based mapping for backwards compatibility
            if (priceUsd == null) {
                val tickerAsset = "thor.${it.ticker}".lowercase()
                priceUsd = poolAssetToPriceMap[tickerAsset]?.toBigDecimal(scale = 8)
            }

            // If still no price found, skip this token
            if (priceUsd == null) {
                return@mapNotNull null
            }

            //Since ninerealms provides prices in USD, we use the USDT rate to convert them into the selected currency
            it.id to mapOf(currency to priceUsd * tetherPrice)
        }
        .toMap()

}


    private suspend fun fetchThorContractPrices(
        tokenList: List<Coin>,
        currency: String
    ) = supervisorScope {
        try {
            val thorTokens =
                Coins.coins[Chain.ThorChain]?.filter {
                    it.contractAddress.startsWith("x/nami") ||
                            it.contractAddress == "x/staking-tcy"
                } ?: emptyList()

            val matchingTokens = tokenList.filter { token ->
                thorTokens.any { it.id.equals(token.id, true) }
            }

            if (matchingTokens.isEmpty()) return@supervisorScope

            val contracts = matchingTokens.map {
                when {
                    it.contractAddress.startsWith("x/nami") ->
                        it.contractAddress.substringAfter("nav-").substringBefore("-rcpt")
                    it.contractAddress == "x/staking-tcy" -> 
                        "thor1z7ejlk5wk2pxh9nfwjzkkdnrq4p2f5rjcpudltv0gh282dwfz6nq9g2cr0"
                    else -> it.contractAddress
                }
            }

            val tokenIds = matchingTokens.map { it.id }

            val tickerUsd = AppCurrency.USD.ticker.lowercase()
            val tetherPrice = if (currency.equals(tickerUsd, ignoreCase = true)) {
                BigDecimal.ONE
            } else {
                fetchTetherPrice()
            }

            val tokenIdToPrices = coroutineScope {
                contracts.zip(tokenIds).mapIndexed { index, (contract, tokenId) ->
                    async {
                        try {
                            val token = matchingTokens[index]

                            val vaultData = thorApi.getThorchainTokenPriceByContract(contract)

                            val priceUsd = if (token.contractAddress == "x/staking-tcy") {
                                val tcyPriceUSD = getCachedPrice("tcy", AppCurrency.USD)
                                    ?: getPriceByPriceProviderId("tcy")

                                val liquidBondSize = vaultData.data.liquidBondSize.toBigDecimalOrNull() ?: BigDecimal.ZERO
                                val liquidBondShares = vaultData.data.liquidBondShares.toBigDecimalOrNull() ?: BigDecimal.ZERO

                                if (liquidBondShares > BigDecimal.ZERO) {
                                    liquidBondSize.divide(liquidBondShares, 8, RoundingMode.DOWN) * tcyPriceUSD
                                } else {
                                    BigDecimal.ONE * tcyPriceUSD
                                }
                            } else {
                                // For NAMI tokens, use navPerShare
                                vaultData.data.navPerShare.toBigDecimalOrNull() ?: BigDecimal.ZERO
                            }

                            tokenId to mapOf(currency to priceUsd * tetherPrice)
                        } catch (e: Exception) {
                            Timber.e(e, "Failed to fetch price for contract: $contract")
                            null
                        }
                    }
                }.awaitAll().filterNotNull().toMap()
            }

            savePrices(tokenIdToPrices, currency)
        } catch (t: Throwable) {
            Timber.e(t, "Could not update YTCY/YRUNE/sTCY prices")
        }
    }

    companion object {
        private const val TETHER_PRICE_PROVIDER_ID = "tether"
    }

    private fun mapThorPoolAsset(contractAddress: String): String {
        val addr = contractAddress.lowercase()

        return try {
            when {
                // simple alphanumeric -> thor.<addr>
                addr.matches(Regex("^[a-z0-9]+$")) ->
                    "thor.$addr"

                // single hyphen pair -> replace hyphen with dot (e.g. bcs-bnb -> bcs.bnb)
                addr.matches(Regex("^[a-z0-9]+-[a-z0-9]+$")) ->
                    addr.replace(
                        "-",
                        "."
                    )

                // special x/… pattern: take the third-from-last segment as the prefix
                // and join the last two segments with '-' to preserve things like `usdc-0x...`
                addr.startsWith("x/") && addr.contains("-") -> {
                    val after = addr.substringAfter("x/")
                    val parts = after.split("-").filter { it.isNotEmpty() }
                    if (parts.size >= 3) {
                        val prefix = parts[parts.size - 3]
                        val tail = parts.subList(
                            parts.size - 2,
                            parts.size
                        ).joinToString("-")
                        "$prefix.$tail"
                    } else if (parts.size == 2) {
                        // fallback: a.b -> parts[0].parts[1]
                        "${parts[0]}.${parts[1]}"
                    } else {
                        // fallback to replacing hyphens with dots
                        after.replace(
                            "-",
                            "."
                        )
                    }
                }

                addr.matches(Regex("^[a-z0-9]+-[a-z0-9]+-0x[0-9a-f]+$")) -> {
                    val i = addr.indexOf('-')
                    addr.substring(
                        0,
                        i
                    ) + "." + addr.substring(i + 1)
                }

                // fallback: replace hyphens with dots
                else -> addr.replace(
                    "-",
                    "."
                )
            }
        } catch (t: Throwable) {
            "thor.$addr"
        }
    }
}
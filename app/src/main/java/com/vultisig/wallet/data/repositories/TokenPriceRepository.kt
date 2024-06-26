package com.vultisig.wallet.data.repositories

import com.vultisig.wallet.data.api.CoinGeckoApi
import com.vultisig.wallet.data.api.CurrencyToPrice
import com.vultisig.wallet.data.db.dao.TokenPriceDao
import com.vultisig.wallet.data.db.models.TokenPriceEntity
import com.vultisig.wallet.data.models.AppCurrency
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.math.BigDecimal
import javax.inject.Inject

internal interface TokenPriceRepository {

    suspend fun getCachedPrice(
        priceProviderId: String,
        appCurrency: AppCurrency,
    ): BigDecimal?

    fun getPrice(
        priceProviderId: String,
        appCurrency: AppCurrency,
    ): Flow<BigDecimal>

    suspend fun refresh(
        priceProviderIds: List<String>,
    )

}


internal class TokenPriceRepositoryImpl @Inject constructor(
    private val appCurrencyRepository: AppCurrencyRepository,
    private val coinGeckoApi: CoinGeckoApi,
    private val tokenPriceDao: TokenPriceDao,
) : TokenPriceRepository {

    private val providerIdToPrice = MutableStateFlow(mapOf<String, CurrencyToPrice>())

    override suspend fun getCachedPrice(
        priceProviderId: String,
        appCurrency: AppCurrency
    ): BigDecimal? = tokenPriceDao
        .getTokenPrice(priceProviderId, appCurrency.ticker.lowercase())
        ?.let { BigDecimal(it) }

    @ExperimentalCoroutinesApi
    override fun getPrice(
        priceProviderId: String,
        appCurrency: AppCurrency,
    ): Flow<BigDecimal> = providerIdToPrice.map {
        it[priceProviderId]
            ?.get(appCurrency.ticker.lowercase())
            ?: BigDecimal.ZERO
    }

    override suspend fun refresh(priceProviderIds: List<String>) {
        val fiatParam = appCurrencyRepository.currency.first().ticker.lowercase()
        val prices = coinGeckoApi.getCryptoPrices(priceProviderIds, listOf(fiatParam))

        prices.forEach { (providerId, currencyToPrice) ->
            currencyToPrice[fiatParam]?.toPlainString()?.let { price ->
                tokenPriceDao.insertTokenPrice(
                    TokenPriceEntity(
                        priceProviderId = providerId,
                        currency = fiatParam,
                        price = price,
                    )
                )
            }
        }

        providerIdToPrice.value = prices
    }

}
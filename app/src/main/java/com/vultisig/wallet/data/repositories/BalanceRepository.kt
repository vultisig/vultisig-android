package com.vultisig.wallet.data.repositories

import com.vultisig.wallet.data.api.ThorChainApi
import com.vultisig.wallet.data.models.FiatValue
import com.vultisig.wallet.data.models.TokenBalance
import com.vultisig.wallet.data.models.TokenValue
import com.vultisig.wallet.models.Chain.thorChain
import com.vultisig.wallet.models.Coin
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapConcat
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.zip
import java.math.RoundingMode
import javax.inject.Inject


internal interface BalanceRepository {

    fun getTokenBalance(
        address: String,
        coin: Coin,
    ): Flow<TokenBalance>

    fun getTokenValue(
        address: String,
        coin: Coin
    ): Flow<TokenValue>

}

internal class BalanceRepositoryImpl @Inject constructor(
    private val thorChainApi: ThorChainApi,
    private val tokenPriceRepository: TokenPriceRepository,
    private val appCurrencyRepository: AppCurrencyRepository,
) : BalanceRepository {

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun getTokenBalance(
        address: String,
        coin: Coin,
    ): Flow<TokenBalance> =
        appCurrencyRepository
            .currency
            .flatMapConcat { currency ->
                tokenPriceRepository
                    .getPrice(coin.priceProviderID, currency)
                    .zip(getTokenValue(address, coin)) { price, balance ->
                        TokenBalance(
                            tokenValue = balance,
                            fiatValue = FiatValue(
                                value = balance.balance
                                    .multiply(price)
                                    .setScale(2, RoundingMode.HALF_UP),
                                currency = currency.ticker,
                            )
                        )
                    }
            }


    override fun getTokenValue(
        address: String,
        coin: Coin,
    ): Flow<TokenValue> = flow {
        emit(TokenValue(when (coin.chain) {
            thorChain -> {
                val listCosmosBalance = thorChainApi.getBalance(address)
                val balance = listCosmosBalance
                    .find { it.denom.equals(coin.ticker, ignoreCase = true) }

                balance?.amount?.toBigInteger() ?: 0.toBigInteger()
            }

            else -> 0.toBigInteger() // TODO support other chains
        }, coin.decimal))
    }

}
package com.vultisig.wallet.data.repositories

import com.vultisig.wallet.data.api.ThorChainApi
import com.vultisig.wallet.data.models.TokenValue
import com.vultisig.wallet.models.Chain.bitcoin
import com.vultisig.wallet.models.Chain.bitcoinCash
import com.vultisig.wallet.models.Chain.dash
import com.vultisig.wallet.models.Chain.dogecoin
import com.vultisig.wallet.models.Chain.litecoin
import com.vultisig.wallet.models.Chain.thorChain
import com.vultisig.wallet.models.Coin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject


internal interface BalanceRepository {

    fun getBalance(coin: Coin): Flow<TokenValue>

}

internal class BalanceRepositoryImpl @Inject constructor(
    private val thorChainApi: ThorChainApi,
) : BalanceRepository {

    override fun getBalance(coin: Coin): Flow<TokenValue> = flow {
        emit(TokenValue(when (coin.chain) {
            thorChain -> {
                val listCosmosBalance = thorChainApi.getBalance(coin.address)
                val balance = listCosmosBalance
                    .find { it.denom.equals(coin.ticker, ignoreCase = true) }

                balance?.amount?.toBigInteger() ?: 0.toBigInteger()
            }

            bitcoin, litecoin, bitcoinCash, dogecoin, dash ->
                0.toBigInteger()

            else -> 0.toBigInteger()
        }, coin.decimal))
    }

}
package com.vultisig.wallet.service

import com.vultisig.wallet.models.Chain
import com.vultisig.wallet.models.Coin
import com.vultisig.wallet.models.getBalance
import com.vultisig.wallet.models.getBalanceInFiat
import java.math.BigDecimal
import javax.inject.Inject

data class Balance(val rawBalance: String, val balanceInFiat: BigDecimal, val balance: BigDecimal)
class BalanceService @Inject constructor(
    private val thorChainService: THORChainService,
) {
    fun getBalance(coin: Coin): Balance {
        when (coin.chain) {
            Chain.thorChain -> {
                val listCosmosBalance = thorChainService.getBalance(coin.address)
                val balance =
                    listCosmosBalance.find { it.denom.equals(coin.ticker, ignoreCase = true) }
                return if (balance != null) {
                    coin.rawBalance = balance.amount.toBigInteger()
                    val balanceInFiat = coin.getBalanceInFiat()
                    Balance(balance.amount, balanceInFiat, coin.getBalance())
                } else {
                    Balance("0", BigDecimal.ZERO, BigDecimal.ZERO)
                }
            }

            Chain.bitcoin, Chain.litecoin, Chain.bitcoinCash, Chain.dogecoin, Chain.dash -> {
                return Balance("0", BigDecimal.ZERO, BigDecimal.ZERO)
            }

            else ->
                return Balance("0", BigDecimal.ZERO, BigDecimal.ZERO)
        }
    }
}
package com.vultisig.wallet.service

import com.vultisig.wallet.models.Chain
import com.vultisig.wallet.models.Coin
import com.vultisig.wallet.models.getBalance
import com.vultisig.wallet.models.getBalanceInFiat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.math.BigDecimal
import javax.inject.Inject

data class Balance(val rawBalance: String, val balanceInFiat: BigDecimal, val balance: BigDecimal)
class BalanceService @Inject constructor(
    private val thorChainService: THORChainService,
) {
    suspend fun getBalance(coin: Coin): Balance = withContext(Dispatchers.IO) {
        when (coin.chain) {
            Chain.thorChain -> {
                val listCosmosBalance = thorChainService.getBalance(coin.address)
                val balance =
                    listCosmosBalance.find { it.denom.equals(coin.ticker, ignoreCase = true) }
                return@withContext if (balance != null) {
                    val newCoin = coin.copy(rawBalance = balance.amount.toBigInteger())
                    val balanceInFiat = newCoin.getBalanceInFiat()
                    Balance(balance.amount, balanceInFiat, newCoin.getBalance())
                } else {
                    Balance("0", BigDecimal.ZERO, BigDecimal.ZERO)
                }
            }

            Chain.bitcoin, Chain.litecoin, Chain.bitcoinCash, Chain.dogecoin, Chain.dash -> {
                return@withContext Balance("0", BigDecimal.ZERO, BigDecimal.ZERO)
            }

            else ->
                return@withContext Balance("0", BigDecimal.ZERO, BigDecimal.ZERO)
        }
    }
}
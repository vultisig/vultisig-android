package com.vultisig.wallet.presenter.home

import android.util.Log
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vultisig.wallet.chains.thorchainHelper
import com.vultisig.wallet.chains.utxoHelper
import com.vultisig.wallet.common.SettingsCurrency
import com.vultisig.wallet.models.Chain
import com.vultisig.wallet.models.Coin
import com.vultisig.wallet.models.Vault
import com.vultisig.wallet.models.getBalance
import com.vultisig.wallet.models.getBalanceInFiatString
import com.vultisig.wallet.service.BalanceService
import com.vultisig.wallet.service.CryptoPriceService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import wallet.core.jni.CoinType
import java.math.BigDecimal
import java.math.BigInteger
import javax.inject.Inject

data class CoinWrapper(
    var coin: Coin,
) {
    fun updateBalance(rawBalance: BigInteger, priceRate: BigDecimal, currency: SettingsCurrency) {
        coin = coin.copy(
            rawBalance = rawBalance,
            priceRate = priceRate,
            currency = currency
        )
        coinBalance.value = coin.getBalance()
        coinBalanceInFiat.value = coin.getBalanceInFiatString()
    }

    val coinBalance: MutableState<BigDecimal> = mutableStateOf(BigDecimal.ZERO)
    val coinBalanceInFiat: MutableState<String> = mutableStateOf("0.00")
}

@HiltViewModel
class VaultDetailViewModel @Inject constructor(
    private val priceService: CryptoPriceService,
    private val balanceService: BalanceService,
) : ViewModel() {
    private val _defaultChains = listOf(Chain.bitcoin, Chain.thorChain)
    private val _coins = MutableLiveData<List<CoinWrapper>>(emptyList())

    val coins: MutableLiveData<List<CoinWrapper>>
        get() = _coins

    val currentVault: MutableLiveData<Vault> = MutableLiveData(Vault("empty"))
    suspend fun getCurrentPrice(coin: Coin): BigDecimal {
        return priceService.getPrice(coin.priceProviderID)
    }

    suspend fun setData(vault: Vault) {
        this.currentVault.value = vault
        applyDefaultChains(vault)
        _coins.value = vault.coins.map { CoinWrapper(it) }
        priceService.updatePriceProviderIDs(vault.coins.map { it.priceProviderID })
        _coins.value?.forEach() { currentCoinWrapper ->
            viewModelScope.launch {
                val currency = priceService.getSettingCurrency()
                val priceRate = getCurrentPrice(currentCoinWrapper.coin)
                var coinRawBalance = BigInteger.ZERO
                withContext(Dispatchers.IO) {
                    val balance = balanceService.getBalance(currentCoinWrapper.coin)
                    coinRawBalance = balance.rawBalance.toBigInteger()
                    Log.d("VaultDetailViewModel", "balance: $coinRawBalance updated")
                }
                // Update the balance in the vault
                vault.coins.forEachIndexed() { index, coin ->
                    if (coin.ticker == currentCoinWrapper.coin.ticker) {
                        vault.coins[index] = coin.copy(
                            rawBalance = coinRawBalance,
                            priceRate = priceRate,
                            currency = currency
                        )
                    }
                }
                withContext(Dispatchers.Main) {
                    currentCoinWrapper.updateBalance(coinRawBalance, priceRate, currency)
                }
            }
        }

    }

    private fun applyDefaultChains(vault: Vault) {
        if (vault.coins.isNotEmpty()) return
        for (item in _defaultChains) {
            when (item) {
                Chain.bitcoin -> {
                    val btcHelper =
                        utxoHelper(CoinType.BITCOIN, vault.pubKeyECDSA, vault.hexChainCode)
                    btcHelper.getCoin()?.let {
                        vault.coins.add(it)
                    }
                }

                Chain.thorChain -> {
                    val thorHelper =
                        thorchainHelper(vault.pubKeyECDSA, vault.hexChainCode)
                    thorHelper.getCoin()?.let {
                        vault.coins.add(it)
                    }
                }

                else -> // TODO: add more chains here
                    return
            }

        }

    }

}
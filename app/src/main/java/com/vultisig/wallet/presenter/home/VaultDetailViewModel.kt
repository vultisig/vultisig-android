package com.vultisig.wallet.presenter.home

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vultisig.wallet.chains.thorchainHelper
import com.vultisig.wallet.chains.utxoHelper
import com.vultisig.wallet.models.Chain
import com.vultisig.wallet.models.Coin
import com.vultisig.wallet.models.Vault
import com.vultisig.wallet.service.BalanceService
import com.vultisig.wallet.service.CryptoPriceService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import wallet.core.jni.CoinType
import java.math.BigDecimal
import javax.inject.Inject

@HiltViewModel
class VaultDetailViewModel @Inject constructor(
    private val priceService: CryptoPriceService,
    private val balanceService: BalanceService,
) : ViewModel() {
    private val _defaultChains = listOf(Chain.bitcoin, Chain.thorChain)
    private val _coins = MutableLiveData<List<Coin>>()

    val coins: MutableLiveData<List<Coin>>
        get() = _coins

    suspend fun getCurrentPrice(coin: Coin): BigDecimal {
        return priceService.getPrice(coin.priceProviderID)
    }

    suspend fun setData(vault: Vault) {
        applyDefaultChains(vault)
        _coins.value = vault.coins
        priceService.updatePriceProviderIDs(vault.coins.map { it.priceProviderID })
        vault.coins.forEach() { currentCoin ->
            viewModelScope.launch {
                currentCoin.currency = priceService.getSettingCurrency()
                currentCoin.priceRate = getCurrentPrice(currentCoin)
                withContext(Dispatchers.IO) {
                    val balance = balanceService.getBalance(currentCoin)
                    currentCoin.rawBalance = balance.rawBalance.toBigInteger()
                }
                // update the coin in the list , thus view will redraw
                _coins.postValue(_coins.value?.map {
                    if (it.ticker.equals(
                            currentCoin.ticker,
                            ignoreCase = true
                        )
                    ) currentCoin else it
                })
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
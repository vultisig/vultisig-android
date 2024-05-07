package com.vultisig.wallet.presenter.home

import android.util.Log
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.vultisig.wallet.chains.utxoHelper
import com.vultisig.wallet.models.Chain
import com.vultisig.wallet.models.Coin
import com.vultisig.wallet.models.Vault
import com.vultisig.wallet.service.CryptoPriceService
import dagger.hilt.android.lifecycle.HiltViewModel
import wallet.core.jni.CoinType
import java.math.BigDecimal
import javax.inject.Inject

@HiltViewModel
class VaultDetailViewModel @Inject constructor(
    private val priceService: CryptoPriceService,
) : ViewModel() {
    private val _defaultChains = listOf(Chain.bitcoin)
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
        vault.coins.forEach() {
            it.priceRate = getCurrentPrice(it)
            Log.d("VaultDetailViewModel", "priceRate: ${it.priceRate}")
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

                else -> // TODO: add more chains here
                    return
            }

        }

    }

}
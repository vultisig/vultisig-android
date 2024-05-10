package com.vultisig.wallet.presenter.chain_coin

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.google.gson.Gson
import com.vultisig.wallet.models.Coin
import com.vultisig.wallet.models.Vault
import com.vultisig.wallet.models.getBalanceInFiat
import com.vultisig.wallet.ui.models.ChainAccountUiModel
import com.vultisig.wallet.ui.navigation.Screen
import dagger.hilt.android.lifecycle.HiltViewModel
import java.math.BigDecimal
import java.math.RoundingMode
import javax.inject.Inject

@HiltViewModel
internal class ChainCoinViewmodel @Inject constructor(savedStateHandle: SavedStateHandle) : ViewModel() {
    private val gson = Gson()
    private val accountJson: String = savedStateHandle.get<String>(Screen.CHAIN_COIN_ACCOUNT_JSON)!!
    private val vaultJson: String = savedStateHandle.get<String>(Screen.CHAIN_COIN_VAULT_JSON)!!
    private val coinsJson: String = savedStateHandle.get<String>(Screen.CHAIN_COIN_COINS_JSON)!!

    val selectedChainAccount: ChainAccountUiModel = gson.fromJson(accountJson, ChainAccountUiModel::class.java)
    val vault: Vault = gson.fromJson(vaultJson, Vault::class.java)
    val coins:List<Coin> = gson.fromJson(coinsJson, Array<Coin>::class.java).asList()
    val totalPrice: BigDecimal = coins.sumOf { it.getBalanceInFiat() }.setScale(2, RoundingMode.HALF_UP)

}
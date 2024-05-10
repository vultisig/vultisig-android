package com.vultisig.wallet.presenter.chain_coin

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vultisig.wallet.data.on_board.db.VaultDB
import com.vultisig.wallet.models.getBalanceInFiat
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.navigation.Screen.ChainCoin.CHAIN_COIN_PARAM_CHAIN_RAW
import com.vultisig.wallet.ui.navigation.Screen.ChainCoin.CHAIN_COIN_PARAM_VAULT_ID
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import java.math.RoundingMode
import javax.inject.Inject

@HiltViewModel
internal class ChainCoinViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val vaultDB: VaultDB,
    private val navigator: Navigator<Destination>,
) : ViewModel() {
    private val chainRaw: String = savedStateHandle.get<String>(CHAIN_COIN_PARAM_CHAIN_RAW)!!
    private val vaultId: String = savedStateHandle.get<String>(CHAIN_COIN_PARAM_VAULT_ID)!!

    val uiModel = MutableStateFlow(ChainCoinUiModel())

    fun loadData() {
        val vault = vaultDB.select(vaultId)
        val coins = vault?.coins?.filter { it.chain.raw == chainRaw }?.toList() ?: emptyList()
        val chainAddress = coins.firstOrNull()?.address ?: ""
        val totalPrice = coins.sumOf { it.getBalanceInFiat() }
            .setScale(2, RoundingMode.HALF_UP).toPlainString()

        uiModel.update {
            it.copy(
                chainName = chainRaw,
                chainAddress = chainAddress,
                coins = coins,
                totalPrice = totalPrice
            )
        }
    }

    fun send() {
        viewModelScope.launch {
            navigator.navigate(Destination.Send)
        }
    }

    fun swap() {
        // TODO navigate to swap screen
    }

    fun selectTokens() {
        viewModelScope.launch {
            navigator.navigate(
                Destination.SelectTokens(
                    vaultId = vaultId,
                    accountId = chainRaw,
                )
            )
        }
    }

}
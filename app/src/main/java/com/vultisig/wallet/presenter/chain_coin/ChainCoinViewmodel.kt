package com.vultisig.wallet.presenter.chain_coin

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.vultisig.wallet.data.on_board.db.VaultDB
import com.vultisig.wallet.models.getBalanceInFiat
import com.vultisig.wallet.ui.navigation.Screen.ChainCoin.CHAIN_COIN_PARAM_CHAIN_RAW
import com.vultisig.wallet.ui.navigation.Screen.ChainCoin.CHAIN_COIN_PARAM_VAULT_ID
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import java.math.RoundingMode
import javax.inject.Inject

@HiltViewModel
internal class ChainCoinViewmodel @Inject constructor(
    private val vaultDB: VaultDB,
    savedStateHandle: SavedStateHandle
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

}
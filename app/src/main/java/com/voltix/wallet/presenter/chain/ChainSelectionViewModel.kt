package com.voltix.wallet.presenter.chain

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import com.voltix.wallet.models.Coin
import com.voltix.wallet.models.Coins

class ChainSelectionViewModel : ViewModel() {
    val tokenList: State<List<Coin>>
        get() = _tokenList
    private val _tokenList : MutableState<List<Coin>> = mutableStateOf(listOf())

    init {
        _tokenList.value = Coins.SupportedCoins
    }
}
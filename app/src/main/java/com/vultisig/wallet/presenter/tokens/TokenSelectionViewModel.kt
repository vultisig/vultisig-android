package com.vultisig.wallet.presenter.tokens

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import com.vultisig.wallet.models.Coin
import com.vultisig.wallet.models.Coins

class TokenSelectionViewModel : ViewModel() {
    val tokenList: State<List<Coin>>
        get() = _tokenList
    private val _tokenList: MutableState<List<Coin>> = mutableStateOf(listOf())

    init {
        _tokenList.value = Coins.SupportedCoins
    }
}
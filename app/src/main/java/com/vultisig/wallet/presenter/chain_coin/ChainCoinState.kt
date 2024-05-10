package com.vultisig.wallet.presenter.chain_coin

import com.vultisig.wallet.models.Coin

data class ChainCoinUiModel(
    val chainName: String = "",
    val chainAddress: String= "",
    val totalPrice:String = "",
    val coins: List<Coin> = emptyList(),
)
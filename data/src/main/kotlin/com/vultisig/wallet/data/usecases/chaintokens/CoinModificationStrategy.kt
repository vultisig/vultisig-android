package com.vultisig.wallet.data.usecases.chaintokens

import com.vultisig.wallet.data.models.Coin

internal interface CoinModificationStrategy {
    fun modify(item: Coin): Coin
}
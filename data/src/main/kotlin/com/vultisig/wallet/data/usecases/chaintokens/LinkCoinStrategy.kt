package com.vultisig.wallet.data.usecases.chaintokens

import com.vultisig.wallet.data.models.Coin

internal object LinkCoinStrategy : CoinModificationStrategy {
    override fun modify(item: Coin): Coin {
        require(item.ticker == LINK_TICKER)
        val isBridgeVersion = item.contractAddress == BRIDGED_LINK_CONTRACT_ADDRESS
        return if (isBridgeVersion)
            item.copy(ticker = item.ticker + " (Bridged)")
        else item
    }
}
package com.vultisig.wallet.data.utils

import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.Coins

fun Coins.getCoinBy(chain: Chain, ticker: String): Coin? {
    return coins[chain]?.first { it.ticker.equals(ticker, ignoreCase = true) }
}
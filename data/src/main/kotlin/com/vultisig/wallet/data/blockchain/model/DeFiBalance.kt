package com.vultisig.wallet.data.blockchain.model

import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import java.math.BigInteger

data class DeFiBalance(
    val chain: Chain,
    val balances: List<Balance>
) {
    data class Balance(
        val coin: Coin,
        val amount: BigInteger,
        val coinRewards: Coin? = null,
        val rewardsAmount: BigInteger = BigInteger.ZERO,
    )
}

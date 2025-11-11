package com.vultisig.wallet.data.blockchain.model

import com.vultisig.wallet.data.models.Coin
import java.math.BigDecimal
import java.math.BigInteger
import java.util.Date


data class StakingDetails(
    val id: String,
    val coin: Coin,
    val stakeAmount: BigInteger,
    val apr: Double?,
    val estimatedRewards: BigDecimal?,
    val nextPayoutDate: Date?,
    val rewards: BigDecimal?,
    val rewardsCoin: Coin?,
)

data class LpDetails(
    val firstCoin: Coin,
    val secondCoin: Coin,
    val firstAmount: BigInteger,
    val secondAmount: BigInteger,
    val apr: Double?,
)
package com.vultisig.wallet.data.api.models.thorchain

import java.math.BigInteger

/** Aggregated RUJI staking balances and rewards for a single address. */
data class RujiStakeBalances(
    val stakeAmount: BigInteger = BigInteger.ZERO,
    val stakeTicker: String = "",
    val rewardsAmount: BigInteger = BigInteger.ZERO,
    val rewardsTicker: String = "USDC",
    val apr: Double = 0.0,
)

package com.vultisig.wallet.data.models

import java.math.BigInteger

/** Amounts are 1e8 fixed-point integers (thornode's native scale, not asset decimals). */
data class ThorChainLpPosition(
    val pool: String,
    val units: BigInteger,
    val runeRedeemValue: BigInteger,
    val assetRedeemValue: BigInteger,
    val annualPercentageRate: Double?,
)

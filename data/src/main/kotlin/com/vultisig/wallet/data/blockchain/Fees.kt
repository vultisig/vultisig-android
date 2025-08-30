package com.vultisig.wallet.data.blockchain

import java.math.BigInteger

sealed interface Fee {
    val amount: BigInteger
}

data class GasFees(
    val price: BigInteger = BigInteger.ZERO,
    val limit: BigInteger = BigInteger.ZERO,
    override val amount: BigInteger = BigInteger.ZERO,
): Fee

data class Eip1559(
    val limit: BigInteger,
    val networkPrice: BigInteger, // base fee per gas
    val maxFeePerGas: BigInteger, // max price
    val maxPriorityFeePerGas: BigInteger, // miner tip
    override val amount: BigInteger,
): Fee

data class BasicFee(
    override val amount: BigInteger
): Fee
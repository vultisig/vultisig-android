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

data class TronFees(
    val maxEnergyRequired: BigInteger, // If UI requires so, at some point we should enhance this with discounts
    val energyRequired: BigInteger,
    val bandwidthRequired: BigInteger,
    override val amount: BigInteger,
): Fee

data class RippleFees(
    val networkFee: BigInteger, // field use for helper in the WC transaction
    val accountActivationFee: BigInteger = BigInteger.ZERO, // fee used for UI
    override val amount: BigInteger // field to show total fee
): Fee

data class BasicFee(
    override val amount: BigInteger
): Fee
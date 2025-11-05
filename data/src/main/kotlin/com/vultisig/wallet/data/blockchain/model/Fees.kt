package com.vultisig.wallet.data.blockchain.model

import java.math.BigInteger

/**
 * Sealed interface representing a generic blockchain transaction fee.
 * All specific fee types should implement this interface.
 */
sealed interface Fee {
    val amount: BigInteger
}

/**
 * Gas-based fees for legacy Ethereum-style transactions.
 *
 * @param price The gas price (in wei per gas unit).
 * @param limit The gas limit for the transaction.
 * @param amount The total fee, usually calculated as price * limit.
 */
data class GasFees(
    val price: BigInteger = BigInteger.ZERO,
    val limit: BigInteger = BigInteger.ZERO,
    override val amount: BigInteger = BigInteger.ZERO
) : Fee

/**
 * Ethereum EIP-1559 type fee.
 *
 * @param limit Maximum gas units allowed for the transaction.
 * @param networkPrice Base fee per gas unit imposed by the network.
 * @param maxFeePerGas Maximum total gas price (including priority fee).
 * @param maxPriorityFeePerGas Tip for miners.
 * @param amount Total fee in wei calculated based on gas used and fees.
 */
data class Eip1559(
    val limit: BigInteger,
    val networkPrice: BigInteger, // Base fee per gas
    val maxFeePerGas: BigInteger, // Max total gas price
    val maxPriorityFeePerGas: BigInteger, // Miner tip
    override val amount: BigInteger
) : Fee

/**
 * Tron blockchain transaction fees.
 *
 * @param maxEnergyRequired Max energy that could be consumed, used as safety margin for helper.
 * most of the time energyRequired is fine as limit, although TRON has maintenance cycle (which could
 * trigger changes in price at the time of broadcasting)
 * @param energyRequired Actual energy consumed for the transaction execution.
 * @param energyDiscounted Discounted energy cost (if user has staked energy or incentives).
 * @param bandwidthRequired Required bandwidth units for the transaction.
 * @param bandwidthDiscounted Discounted bandwidth usage (if applicable).
 * @param amount Final fee in SUN after discounts applied.
 */
data class TronFees(
    val maxEnergyRequired: BigInteger = BigInteger.ZERO,
    val energyRequired: BigInteger = BigInteger.ZERO,
    val energyDiscounted: BigInteger = BigInteger.ZERO,
    val bandwidthRequired: BigInteger = BigInteger.ZERO,
    val bandwidthDiscounted: BigInteger = BigInteger.ZERO,
    override val amount: BigInteger
) : Fee

/**
 * Ripple (XRP) transaction fees.
 *
 * @param networkFee Base network fee for transaction processing.
 * @param accountActivationFee Additional fee if the recipient account is new.
 * @param amount Total fee in drops (smallest XRP unit) including activation if applicable.
 */
data class RippleFees(
    val networkFee: BigInteger,
    val accountActivationFee: BigInteger = BigInteger.ZERO,
    override val amount: BigInteger
) : Fee

/**
 * Generic fee type when no specific blockchain logic is required.
 */
data class BasicFee(
    override val amount: BigInteger
) : Fee
